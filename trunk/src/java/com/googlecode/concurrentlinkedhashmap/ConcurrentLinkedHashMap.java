/*
 * Copyright 2010 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ConcurrentMap} with a doubly-linked list running through its
 * entries.
 * <p>
 * This class provides the same semantics as a {@link ConcurrentHashMap} in
 * terms of iterators, acceptable keys, and concurrency characteristics, but
 * perform slightly worse due to the added expense of maintaining the linked
 * list. It differs from {@link java.util.LinkedHashMap} in that it does not
 * provide predictable iteration order.
 * <p>
 * This map is intended to be used for caches and provides a <i>least recently
 * used</i> eviction policy. This policy is based on the observation that
 * entries that have been used recently will likely be used again soon. It
 * provides a good approximation of the optimal algorithm.
 *
 * @author bmanes@gmail.com (Ben Manes)
 * @see <tt>http://code.google.com/p/concurrentlinkedhashmap/</tt>
 */
@ThreadSafe
public final class ConcurrentLinkedHashMap<K, V> extends AbstractMap<K, V>
    implements ConcurrentMap<K, V>, Serializable {

  // This class performs a best-effort bounding of a ConcurrentHashMap using a
  // page-replacement algorithm to determine which entries to evict when the
  // capacity is exceeded. The map supports non-blocking reads and concurrent
  // writes across different segments.
  //
  // The page replacement algorithm's data structures are kept casually
  // consistent with the map. The ordering of writes to a segment is
  // sequentially consistent, but the ordering of writes between different
  // segments is not. An update to the map and recording of reads may not be
  // immediately reflected on the algorithm's data structures. These structures
  // are guarded by a lock and operations are applied in batches to avoid lock
  // contention. The penalty of applying the batches is spread across threads
  // so that the amortized cost is slightly higher than performing just the
  // ConcurrentHashMap operation.
  //
  // This implementation uses a global write queue and per-segment read queues
  // to record a memento of the the additions, removals, and accesses that were
  // performed on the map. The write queue is drained at the first opportunity
  // and a read queue is drained when it exceeds its capacity threshold.

  /**
   * Number of cache reorder operations that can be buffered per segment before
   * the cache's ordering information is updated. This is used to avoid lock
   * contention by recording a memento of reads and delaying a lock acquisition
   * until the threshold is crossed or a mutation occurs.
   */
  static final int REORDER_THRESHOLD = 64;

  /** The maximum number of segments to allow. */
  static final int MAX_SEGMENTS = 1 << 16; // slightly conservative

  /** A queue that discards all entries. */
  static final Queue nullQueue = new DiscardingQueue();

  /** The backing data store holding the key-value associations. */
  final ConcurrentMap<K, Node<K, V>> data;
  final int concurrencyLevel;

  /**
   * These fields mirrors the lock striping on ConcurrentHashMap to order
   * the write operations. This allows the write queue to be consistent.
   */
  final int segments;
  final int segmentMask;
  final int segmentShift;
  final Lock[] segmentLock;

  /** These fields provide support to bound the map by a maximum capacity. */
  @GuardedBy("evictionLock")
  // must write under lock
  volatile int weightedSize;
  @GuardedBy("evictionLock")
  final Node<K, V> sentinel;

  volatile int capacity;
  final Lock evictionLock;
  final Weigher<V> weigher;
  final Queue<Runnable> writeQueue;
  final Queue<Node<K, V>>[] reorderQueue;
  final AtomicInteger[] reorderQueueLength;

  /** A listener is notified when an entry is evicted. */
  final Queue<Node<K, V>> listenerQueue;
  final EvictionListener<K, V> listener;

  /**
   * Creates an instance based on the builder's configuration.
   */
  @SuppressWarnings("unchecked")
  private ConcurrentLinkedHashMap(Builder<K, V> builder) {
    // The shift and mask used by ConcurrentHashMap to select the segment that
    // a key is associated with. This avoids lock contention by ensuring that
    // lock selected by this decorator parallels the one used by the data store
    // so that concurrent writes for different segments do not contend.
    concurrencyLevel = (builder.concurrencyLevel > MAX_SEGMENTS)
        ? MAX_SEGMENTS : builder.concurrencyLevel;
    int sshift = 0;
    int ssize = 1;
    while (ssize < concurrencyLevel) {
        ++sshift;
        ssize <<= 1;
    }
    segmentShift = 32 - sshift;
    segmentMask = ssize - 1;
    segments = ssize;
    segmentLock = new Lock[segments];

    // The data store and its maximum capacity
    data = new ConcurrentHashMap<K, Node<K, V>>(builder.maximumCapacity, 0.75f, concurrencyLevel);
    capacity = builder.maximumCapacity;

    // The eviction support
    weigher = builder.weigher;
    sentinel = new Node<K, V>();
    evictionLock = new ReentrantLock();
    writeQueue = new ConcurrentLinkedQueue<Runnable>();
    reorderQueueLength = new AtomicInteger[segments];
    reorderQueue = (Queue<Node<K, V>>[]) new Queue[segments];
    for (int i=0; i<segments; i++) {
      segmentLock[i] = new ReentrantLock();
      reorderQueueLength[i] = new AtomicInteger();
      reorderQueue[i] = new ConcurrentLinkedQueue<Node<K, V>>();
    }

    // The notification listener and event queue
    listener = builder.listener;
    listenerQueue = (listener == DiscardingListener.INSTANCE)
        ? (Queue<Node<K, V>>) nullQueue
        : new ConcurrentLinkedQueue<Node<K, V>>();
  }

  /**
   * Asserts that the object is not null.
   */
  private static void checkNotNull(Object o, String message) {
    if (o == null) {
      throw new NullPointerException(message);
    }
  }

  /* ---------------- Eviction Support -------------- */

  /**
   * Retrieves the maximum capacity of the map.
   *
   * @return the maximum capacity
   */
  public int capacity() {
    return capacity;
  }

  /**
   * Sets the maximum capacity of the map and eagerly evicts entries until it
   * shrinks to the appropriate size.
   *
   * @param capacity the maximum capacity of the map
   * @throws IllegalArgumentException if the capacity is negative
   */
  public void setCapacity(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException();
    }
    this.capacity = capacity;

    evictionLock.lock();
    try {
      drainWriteQueue();
      while (evict()) {
        // repeat
      }
    } finally {
      evictionLock.unlock();
    }
  }

  /**
   * Determines whether the map has exceeded its capacity.
   *
   * @return if the map has overflowed and an entry should be evicted
   */
  private boolean isOverflow() {
    return weightedSize > capacity;
  }

  /**
   * Evicts a single entry if the map exceeds its maximum capacity and appends
   * the entry to the listener queue for processing.
   *
   * @return if an entry was removed
   */
  @GuardedBy("evictionLock")
  private boolean evict() {
    // Attempts to evicts an entry from the map if it exceeds the maximum
    // capacity. If the eviction fails due to a concurrent removal of the
    // victim, that removal cancels out the addition that triggered this
    // eviction. The victim is eagerly unlinked before the removal task so
    // that if are other pending prior additions then a new victim will be
    // chosen for removal.

    if (isOverflow()) {
      Node<K, V> node = sentinel.next;
      // Notify the listener if the entry was evicted
      if (data.remove(node.key, node)) {
        listenerQueue.add(node);
      }
      weightedSize -= node.weightedValue.weight;
      node.remove();
      return true;
    }
    return false;
  }

  /**
   * Determines the segment that the key is associated with. To avoid lock
   * contention this should always parallel the segment selected by
   * {@link ConcurrentHashMap} so that concurrent writes for different
   * segments do not contend.
   *
   * @param key the entry's key
   * @return the segment index
   */
  int segmentFor(Object key) {
    int hash = hash(key.hashCode());
    return (hash >>> segmentShift) & segmentMask;
  }

  /**
   * Applies a supplemental hash function to a given hashCode, which
   * defends against poor quality hash functions. This is critical
   * because ConcurrentHashMap uses power-of-two length hash tables,
   * that otherwise encounter collisions for hashCodes that do not
   * differ in lower or upper bits.
   *
   * @param hashCode the key's hashCode
   * @return an improved hashCode
   */
  private static int hash(int hashCode) {
    // Spread bits to regularize both segment and index locations,
    // using variant of single-word Wang/Jenkins hash.
    hashCode += (hashCode <<  15) ^ 0xffffcd7d;
    hashCode ^= (hashCode >>> 10);
    hashCode += (hashCode <<   3);
    hashCode ^= (hashCode >>>  6);
    hashCode += (hashCode <<   2) + (hashCode << 14);
    return hashCode ^ (hashCode >>> 16);
  }

  /**
   * Adds the entry to the reorder queue for a future update to the page
   * replacement algorithm.
   *
   * @param node the entry that was read
   * @return the size of the queue
   */
  private int appendToReorderQueue(Node<K, V> node) {
    int segment = node.segment;
    reorderQueue[segment].add(node);
    return reorderQueueLength[segment].incrementAndGet();
  }

  /**
   * Attempts to acquire the eviction lock and apply pending updates to the
   * eviction algorithm.
   *
   * @param segment the segment's reorder queue to drain
   * @param onlyIfWrites attempts the drain the eviction queues only if there
   *     are pending writes
   */
  private void attemptToDrainEvictionQueues(int segment, boolean onlyIfWrites) {
    if (writeQueue.isEmpty() && onlyIfWrites) {
      return;
    }
    if (evictionLock.tryLock()) {
      try {
        drainReorderQueue(segment);
        drainWriteQueue();
      } finally {
        evictionLock.unlock();
      }
    }
  }

  /**
   * Attempts to acquire the eviction lock and apply pending write updates to
   * the eviction algorithm.
   */
  private void attemptToDrainWriteQueues() {
    if (!writeQueue.isEmpty() && evictionLock.tryLock()) {
      try {
        drainWriteQueue();
      } finally {
        evictionLock.unlock();
      }
    }
  }

  /**
   * Applies the pending updates to the list.
   */
  @GuardedBy("evictionLock")
  void drainWriteQueue() {
    Runnable task;
    while ((task = writeQueue.poll()) != null) {
      task.run();
    }
  }

  /**
   * Applies the pending reorderings to the list.
   *
   * @param segment the segment's reorder queue to drain
   */
  @GuardedBy("evictionLock")
  void drainReorderQueue(int segment) {
    // While the queue is being drained it may be concurrently appended to. The
    // number of elements removed are tracked so that the length can be
    // decremented by the delta rather than be set to zero.
    int delta = 0;
    Node<K, V> node;
    Queue<Node<K, V>> queue = reorderQueue[segment];
    while ((node = queue.poll()) != null) {
      // skip the node if appended to queue during its removal
      if (node.isLinked()) {
        node.moveToTail();
      }
      delta--;
    }
    reorderQueueLength[segment].addAndGet(delta);
  }

  /**
   * Performs the post-processing of eviction events.
   *
   * @param segment the segment's reorder queue to drain
   * @param onlyIfWrites attempts the drain the eviction queues only if there
   *     are pending writes
   */
  private void processEvents(int segment, boolean onlyIfWrites) {
    attemptToDrainEvictionQueues(segment, onlyIfWrites);
    notifyListeners();
  }

  /**
   * Notifies the listener of entries that were evicted.
   */
  private void notifyListeners() {
    Node<K, V> node;
    while ((node = listenerQueue.poll()) != null) {
      listener.onEviction(node.key, node.weightedValue.value);
    }
  }

  /**
   * Adds a node to the list and evicts an entry on overflow.
   */
  private final class AddTask implements Runnable {
    private final Node<K, V> node;
    private final int weight;

    AddTask(Node<K, V> node, int weight) {
      this.weight = weight;
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      weightedSize += weight;
      node.appendToTail();
      evict();
    }
  }

  /**
   * Removes a node from the list.
   */
  private final class RemovalTask implements Runnable {
    private final Node<K, V> node;

    RemovalTask(Node<K, V> node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      if (node.isLinked()) {
        weightedSize -= node.weightedValue.weight;
        node.remove();
      }
    }
  }

  /**
   * Updates the weighted size and evicts an entry on overflow.
   */
  private final class UpdateTask implements Runnable {
    private int weightDifference;

    public UpdateTask(int weightDifference) {
      this.weightDifference = weightDifference;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      weightedSize += weightDifference;
      evict();
    }
  }

  /* ---------------- Concurrent Map Support -------------- */

  @Override
  public boolean isEmpty() {
    attemptToDrainWriteQueues();
    return data.isEmpty();
  }

  @Override
  public int size() {
    attemptToDrainWriteQueues();
    return data.size();
  }

  /**
   * Returns the weighted size in this map.
   *
   * @return the combined weight of the values in this map
   */
  public int weightedSize() {
    attemptToDrainWriteQueues();
    return weightedSize;
  }

  @Override
  public void clear() {
    // The alternative is to iterate through the keys and call #remove(), which
    // unnecessarily adds contention on the eviction lock and the write queue.
    // Instead the nodes in the list are copied, the list is cleared, and the
    // nodes are conditionally removed. The prev and next fields are null'ed
    // out to reduce GC pressure.
    List<Node<K, V>> nodes;
    evictionLock.lock();
    try {
      drainWriteQueue();
      nodes = new ArrayList<Node<K, V>>(weightedSize);
      weightedSize = 0;

      Node<K, V> current = sentinel.next;
      while (current != sentinel) {
        nodes.add(current);
        current = current.next;
        current.prev.prev = null;
        current.prev.next = null;
      }
      sentinel.next = sentinel;
      sentinel.prev = sentinel;
    } finally {
      evictionLock.unlock();
    }

    for (Node<K, V> node : nodes) {
      data.remove(node.key, node);
    }
  }

  @Override
  public boolean containsKey(Object key) {
    checkNotNull(key, "null key");
    processEvents(segmentFor(key), true);

    return data.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    checkNotNull(value, "value");
    attemptToDrainWriteQueues();

    for (Node<K, V> node : data.values()) {
      if (node.weightedValue.value.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    checkNotNull(key, "null key");

    // As read are the common case they should be performed lock-free to avoid
    // blocking other readers. If the entry was found then a reorder operation
    // is scheduled on the queue to be applied sometime in the future. The
    // draining of the queues should be delayed until either the reorder
    // threshold has been exceeded or if there is a pending write.
    int segment;
    V value = null;
    boolean delayReorder = true;
    Node<K, V> node = data.get(key);
    if (node == null) {
      segment = segmentFor(key);
    } else {
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
      value = node.weightedValue.value;
      segment = node.segment;
    }
    processEvents(segment, delayReorder);
    return value;
  }

  @Override
  public V put(K key, V value) {
    return put(key, value, false);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return put(key, value, true);
  }

  /**
   * Adds a node to the list and the data store. If an existing node exists
   * then its value is updated if allowed.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @param onlyIfAbsent a write is performed only the key is not already
   *     associated with a value
   * @return the prior value in the data store or null if no mapping was found
   */
  private V put(K key, V value, boolean onlyIfAbsent) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. If a remove occurs immediately after the
    // put, the concurrent insertion into the queue might allow the removal to
    // be processed first which would corrupt the capacity constraint. The
    // locking is kept slim and if the insertion fails then the operation is
    // treaded as a read so a reordering operation is scheduled.
    Node<K, V> prior;
    V oldValue = null;
    int weightedDifference = 0;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = new WeightedValue<V>(value, weight);
    Node<K, V> node = new Node<K, V>(key, weightedValue, segment, sentinel);
    Runnable task = new AddTask(node, weight);

    // maintain per-segment write ordering
    lock.lock();
    try {
      prior = data.putIfAbsent(node.key, node);
      if (prior == null) {
        writeQueue.add(task);
      } else {
        if (onlyIfAbsent) {
          oldValue = prior.weightedValue.value;
        } else {
          weightedDifference = weight - prior.weightedValue.weight;
          oldValue = prior.getAndSetValue(node.weightedValue);
        }
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (prior != null) {
      if (weightedDifference != 0) {
        writeQueue.add(new UpdateTask(weightedDifference));
      }
      appendToReorderQueue(prior);
    }

    boolean delayReorder = (oldValue == null);
    processEvents(segment, delayReorder);
    return oldValue;
  }

  @Override
  public V remove(Object key) {
    checkNotNull(key, "null key");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. The ordering of the ConcurrentHashMap's
    // insertion and removal for an entry is handled by its segment lock. The
    // insertion into the write queue after #putIfAbsent()'s is ensured through
    // this lock. This behavior allows shrinking the lock's critical section.
    V value = null;
    Node<K, V> node;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];

    node = data.remove(key);
    if (node != null) {
      value = node.weightedValue.value;
      Runnable task = new RemovalTask(node);

      // maintain per-segment write ordering
      lock.lock();
      try {
        writeQueue.add(task);
      } finally {
        lock.unlock();
      }
    }

    // perform outside of lock
    processEvents(segment, true);
    return value;
  }

  @Override
  public boolean remove(Object key, Object value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. The lock enforces that other mutations
    // completed, the read value isn't stale, and that the removal is ordered.
    Node<K, V> node;
    boolean removed = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];

    // maintain per-segment write ordering
    lock.lock();
    try {
      node = data.get(key);
      if ((node != null) && node.weightedValue.value.equals(value)) {
        writeQueue.add(new RemovalTask(node));
        data.remove(key);
        removed = true;
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    processEvents(segment, true);
    return removed;
  }

  @Override
  public V replace(K key, V value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. The lock enforces that other mutations
    // completed, the read value isn't stale, and that the replacement is
    // ordered.
    V prior = null;
    Node<K, V> node;
    int weightedDifference = 0;
    boolean delayReorder = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = new WeightedValue<V>(value, weight);

    lock.lock();
    try {
      node = data.get(key);
      if (node != null) {
        weightedDifference = weight - node.weightedValue.weight;
        prior = node.getAndSetValue(weightedValue);
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (node != null) {
      if (weightedDifference != 0) {
        writeQueue.add(new UpdateTask(weightedDifference));
      }
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
    }
    processEvents(segment, delayReorder);
    return prior;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkNotNull(key, "null key");
    checkNotNull(oldValue, "null oldValue");
    checkNotNull(newValue, "null newValue");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. The lock enforces that other mutations
    // completed, the read value isn't stale, and that the replacement is
    // ordered.
    Node<K, V> node;
    boolean replaced;
    boolean delayReorder = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(newValue);
    WeightedValue<V> oldWeightedValue = null;
    WeightedValue<V> newWeightedValue = new WeightedValue<V>(newValue, weight);

    lock.lock();
    try {
      node = data.get(key);
      if (node != null) {
        oldWeightedValue = node.casValue(oldValue, newWeightedValue);
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (node != null) {
      if (oldWeightedValue != null) {
        writeQueue.add(new UpdateTask(newWeightedValue.weight - oldWeightedValue.weight));
      }
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
    }
    processEvents(segment, delayReorder);
    return (oldWeightedValue != null);
  }

  @Override
  public Set<K> keySet() {
    return new KeySet();
  }

  @Override
  public Collection<V> values() {
    return new Values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  /**
   * The weighted value.
   */
  static final class WeightedValue<V> {
    final int weight;
    final V value;

    public WeightedValue(V value, int weight) {
      if (weight < 1) {
        throw new IllegalArgumentException("weight must be positive");
      }
      this.weight = weight;
      this.value = value;
    }
  }

  /**
   * A node on the double-linked list. This list cross-cuts the data store.
   */
  static final class Node<K, V> {
    final Node<K, V> sentinel;
    Node<K, V> prev;
    Node<K, V> next;

    final K key;
    final int segment;
    volatile WeightedValue<V> weightedValue;

    /** Creates a new sentinel node. */
    public Node() {
      this.sentinel = this;
      this.segment = -1;
      this.key = null;
      this.prev = this;
      this.next = this;
    }

    /** Creates a new, unlinked node. */
    public Node(K key, WeightedValue<V> weightedValue, int segment, Node<K, V> sentinel) {
      this.weightedValue = weightedValue;
      this.sentinel = sentinel;
      this.segment = segment;
      this.key = key;
      this.prev = null;
      this.next = null;
    }

    /** Retrieves and updates the value. */
    @GuardedBy("segmentLock")
    public V getAndSetValue(WeightedValue<V> value) {
      // not atomic as always performed under lock
      WeightedValue<V> oldValue = this.weightedValue;
      this.weightedValue = value;
      return oldValue.value;
    }

    /** Updates the value if it is equal to the expected value. */
    @GuardedBy("segmentLock")
    public WeightedValue<V> casValue(V expect, WeightedValue<V> update) {
      // not atomic as always performed under lock
      WeightedValue<V> weightedValue = this.weightedValue;
      if (weightedValue.value.equals(expect)) {
        this.weightedValue = update;
        return weightedValue;
      }
      return null;
    }

    /** Removes the node from the list. */
    @GuardedBy("evictionLock")
    public void remove() {
      prev.next = next;
      next.prev = prev;
      // null to reduce GC pressure
      prev = next = null;
    }

    /** Appends the node to the tail of the list. */
    @GuardedBy("evictionLock")
    public void appendToTail() {
      prev = sentinel.prev;
      next = sentinel;
      sentinel.prev.next = this;
      sentinel.prev = this;
    }

    /** Moves the node to the tail of the list. */
    @GuardedBy("evictionLock")
    public void moveToTail() {
      if (next != sentinel) {
        prev.next = next;
        next.prev = prev;
        appendToTail();
      }
    }

    /** Whether the node is linked on the list. */
    @GuardedBy("evictionLock")
    public boolean isLinked() {
      return (next != null);
    }

    @Override
    public String toString() {
      return key + "=" + ((this == sentinel) ? "null" : weightedValue.value);
    }
  }

  /**
   * An adapter to safely externalize the keys.
   */
  private final class KeySet extends AbstractSet<K> {
    private final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public void clear() {
      map.clear();
    }

    @Override
    public Iterator<K> iterator() {
      return new KeyIterator();
    }

    @Override
    public boolean contains(Object obj) {
      return containsKey(obj);
    }

    @Override
    public boolean remove(Object obj) {
      return (map.remove(obj) != null);
    }

    @Override
    public Object[] toArray() {
      return map.data.keySet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      return map.data.keySet().toArray(array);
    }
  }

  /**
   * An adapter to safely externalize the key iterator.
   */
  private final class KeyIterator implements Iterator<K> {
    private final EntryIterator iterator = new EntryIterator(data.values().iterator());

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public K next() {
      return iterator.next().getKey();
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }

  /**
   * An adapter to safely externalize the values.
   */
  private final class Values extends AbstractCollection<V> {

    @Override
    public int size() {
      return ConcurrentLinkedHashMap.this.size();
    }

    @Override
    public void clear() {
      ConcurrentLinkedHashMap.this.clear();
    }

    @Override
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    @Override
    public boolean contains(Object o) {
      return containsValue(o);
    }
  }

  /**
   * An adapter to safely externalize the value iterator.
   */
  private final class ValueIterator implements Iterator<V> {
    private final EntryIterator iterator = new EntryIterator(data.values().iterator());

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public V next() {
      return iterator.next().getValue();
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }

  /**
   * An adapter to safely externalize the entries.
   */
  private final class EntrySet extends AbstractSet<Entry<K, V>> {
    private final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public void clear() {
      map.clear();
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
      return new EntryIterator(map.data.values().iterator());
    }

    @Override
    public boolean contains(Object obj) {
      if (!(obj instanceof Entry)) {
        return false;
      }
      Entry<?, ?> entry = (Entry<?, ?>) obj;
      Node<K, V> node = map.data.get(entry.getKey());
      return (node != null) && (node.weightedValue.value.equals(entry.getValue()));
    }

    @Override
    public boolean add(Entry<K, V> entry) {
      return (map.putIfAbsent(entry.getKey(), entry.getValue()) == null);
    }

    @Override
    public boolean remove(Object obj) {
      if (!(obj instanceof Entry)) {
        return false;
      }
      Entry<?, ?> entry = (Entry<?, ?>) obj;
      return map.remove(entry.getKey(), entry.getValue());
    }
  }

  /**
   * An adapter to safely externalize the entry iterator.
   */
  private final class EntryIterator implements Iterator<Entry<K, V>> {
    private final Iterator<Node<K, V>> iterator;
    private Node<K, V> current;

    public EntryIterator(Iterator<Node<K, V>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Entry<K, V> next() {
      current = iterator.next();
      return new WriteThroughEntry(current);
    }

    @Override
    public void remove() {
      if (current == null) {
        throw new IllegalStateException();
      }
      ConcurrentLinkedHashMap.this.remove(current.key, current.weightedValue.value);
      current = null;
    }
  }

  /**
   * An entry that allows updates to write through to the map.
   */
  @SuppressWarnings("serial")
  private final class WriteThroughEntry extends SimpleEntry<K, V> {

    public WriteThroughEntry(Node<K, V> node) {
      super(node.key, node.weightedValue.value);
    }

    @Override
    public V setValue(V value) {
      put(getKey(), value);
      return super.setValue(value);
    }
  }

  /**
   * A queue that discards all additions and is always empty.
   */
  private static final class DiscardingQueue<E> extends AbstractQueue<E> {
    @Override
    public boolean add(E e) {
      return true;
    }
    @Override
    public boolean offer(E e) {
      return true;
    }
    @Override
    public E poll() {
      return null;
    }
    @Override
    public E peek() {
      return null;
    }
    @Override
    public int size() {
      return 0;
    }
    @Override
    public Iterator<E> iterator() {
      return Collections.<E>emptyList().iterator();
    }
  }

  /**
   * A listener that ignores all notifications.
   */
  private enum DiscardingListener implements EvictionListener {
    INSTANCE;

    @Override
    public void onEviction(Object key, Object value) {}
  }

  /* ---------------- Serialization Support -------------- */

  private static final long serialVersionUID = 1;

  private Object writeReplace() {
    return new SerializationProxy<K, V>(this);
  }

  private void readObject(ObjectInputStream stream) throws InvalidObjectException {
    throw new InvalidObjectException("Proxy required");
  }

  /**
   * A proxy that is serialized instead of the map. The page-replacement
   * algorithm's data structures are not serialized so the deserialized
   * instance contains only the entries. This is acceptable as caches hold
   * transient data that is recomputable and serialization would tend to be
   * used as a fast warm-up process.
   */
  private static final class SerializationProxy<K, V> implements Serializable {
    private final EvictionListener<K, V> listener;
    private final int concurrencyLevel;
    private final Weigher<V> weigher;
    private final Map<K, V> data;
    private final int capacity;

    SerializationProxy(ConcurrentLinkedHashMap<K, V> map) {
      concurrencyLevel = map.concurrencyLevel;
      data = new HashMap<K, V>(map.weightedSize);
      listener = map.listener;
      capacity = map.capacity;
      weigher = map.weigher;

      for (Node<K, V> node : map.data.values()) {
        data.put(node.key, node.weightedValue.value);
      }
    }

    private Object readResolve() {
      ConcurrentLinkedHashMap<K, V> map = new Builder<K, V>()
          .concurrencyLevel(concurrencyLevel)
          .maximumCapacity(capacity)
          .listener(listener)
          .weigher(weigher)
          .build();
      map.putAll(data);
      return map;
    }

    private static final long serialVersionUID = 0;
  }

  /* ---------------- Builder -------------- */

  /**
   * A builder that creates {@link ConcurrentLinkedHashMap} instances.
   */
  @SuppressWarnings("unchecked")
  public static final class Builder<K, V> {
    private EvictionListener<K, V> listener;
    private int concurrencyLevel;
    private int maximumCapacity;
    private Weigher<V> weigher;

    public Builder() {
      maximumCapacity = -1;
      concurrencyLevel = 16;
      weigher = Weighers.singleton();
      listener = (EvictionListener<K, V>) DiscardingListener.INSTANCE;
    }

    /**
     * Specifies the maximum weighted capacity to coerces to. The size may
     * exceed it temporarily.
     *
     * @param maximumCapacity the weighted threshold to bound the map by
     */
    public Builder<K, V> maximumCapacity(int maximumCapacity) {
      this.maximumCapacity = maximumCapacity;
      return this;
    }

    /**
     * Specifies the estimated number of concurrently updating threads. The
     * implementation performs internal sizing to try to accommodate this many
     * threads (default <tt>16</tt>).
     *
     * @param concurrencyLevel the estimated number of concurrently updating
     *     threads
     */
    public Builder<K, V> concurrencyLevel(int concurrencyLevel) {
      this.concurrencyLevel = concurrencyLevel;
      return this;
    }

    /**
     * Specifies an optional listener that is registered for notification when
     * an entry is evicted.
     *
     * @param listener the object to forward evicted entries to
     */
    public Builder<K, V> listener(EvictionListener<K, V> listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Specifies an algorithm to determine how many the units of capacity a
     * value consumes. The default algorithm bounds the map by the number of
     * key-value pairs by giving each entry a weight of <tt>1</tt>.
     *
     * @param weigher the algorithm to determine a value's weight
     */
    public Builder<K, V> weigher(Weigher<V> weigher) {
      this.weigher = weigher;
      return this;
    }

    /**
     * Creates a new {@link ConcurrentLinkedHashMap} instance.
     *
     * @throws IllegalArgumentException if the maximum capacity is less than
     *     zero, the concurrency level is not positive, or if a null instance
     *     is specified for the listener or value weigher
     */
    public ConcurrentLinkedHashMap<K, V> build() {
      if ((maximumCapacity < 0) || (concurrencyLevel <= 0) ||
          (listener == null) || (weigher == null)) {
        throw new IllegalArgumentException();
      }
      return new ConcurrentLinkedHashMap<K, V>(this);
    }
  }
}
