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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
 * A hash table supporting full concurrency of retrievals, adjustable expected
 * concurrency for updates, and a maximum capacity to bound the map by. This
 * implementation differs from {@link ConcurrentHashMap} in that it maintains a
 * page replacement algorithm that is used to evict an entry when the map has
 * exceeded its capacity. Unlike the <tt>Java Collections Framework</tt>, this
 * map does not have a publicly visible constructor and instances are created
 * through a {@link Builder}.
 * <p>
 * An entry is evicted from the map when the <tt>weighted capacity</tt> exceeds
 * the <tt>maximum weighted capacity</tt> threshold. A {@link Weigher} instance
 * determines how many units of capacity that a value consumes. The default
 * singleton weigher algorithm assigns each value a weight of <tt>1</tt> to
 * bound the map by the number of key-value pairs. A map that holds collections
 * may weigh values by the number of elements in the collection and bound the
 * map by the total number of elements it contains. A change to a value that
 * modifies its weight requires that an update operation is performed on the
 * map.
 * <p>
 * An {@link EvictionListener} may be supplied for notification when an entry is
 * evicted from the map. This listener is invoked on a caller's thread and will
 * not block other threads from operating on the map. An implementation should
 * be aware that the caller's thread will not expect long execution times or
 * failures as a side effect of the listener being notified. Execution safety
 * and a fast turn around time can be achieved by performing the operation
 * asynchronously, such as by submitting a task to an
 * {@link java.util.concurrent.ExecutorService}.
 * <p>
 * The <tt>concurrency level</tt> determines the number of threads that can
 * concurrently modify the table. Using a significantly higher or lower value
 * than needed can waste space or lead to thread contention, but an estimate
 * within an order of magnitude of the ideal value does not usually have a
 * noticeable impact. Because placement in hash tables is essentially random,
 * the actual concurrency will vary.
 * <p>
 * This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 * <p>
 * Like {@link java.util.Hashtable} but unlike {@link HashMap}, this class
 * does <em>not</em> allow <tt>null</tt> to be used as a key or value.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
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
  static final int MAXIMUM_SEGMENTS = 1 << 16; // slightly conservative

  /** The maximum weighted capacity of the map. */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /** The maximum weight of a value. */
  static final int MAXIMUM_WEIGHT = 1 << 29;

  /** The percentage of the cache which is dedicated to hot blocks. */
  static final float HOT_RATE = 0.99f;

  /** A queue that discards all entries. */
  static final Queue discardingQueue = new DiscardingQueue();

  /** The backing data store holding the key-value associations. */
  final ConcurrentMap<K, Node> data;
  final int concurrencyLevel;

  // These fields mirror the lock striping on ConcurrentHashMap to order
  // the write operations. This allows the write queue to be consistent.
  final int segments;
  final int segmentMask;
  final int segmentShift;
  final Lock[] segmentLock;

  // These fields provide support to bound the map by a maximum capacity.
  @GuardedBy("evictionLock")
  final Node sentinel;

  @GuardedBy("evictionLock") // must write under lock
  volatile int weightedSize;
  @GuardedBy("evictionLock") // must write under lock
  volatile int maximumWeightedSize;

  @GuardedBy("evictionLock")
  int hotWeightedSize;
  @GuardedBy("evictionLock")
  int maximumHotWeightedSize;

  final Lock evictionLock;
  final Weigher<V> weigher;
  final Queue<Runnable> writeQueue;
  final Queue<Node>[] reorderQueue;
  final AtomicInteger[] reorderQueueLength;

  // A listener is notified when an entry is evicted.
  final Queue<Node> listenerQueue;
  final EvictionListener<K, V> listener;

  /**
   * Creates an instance based on the builder's configuration.
   */
  @SuppressWarnings("unchecked")
  private ConcurrentLinkedHashMap(Builder<K, V> builder) {
    // The shift and mask used by ConcurrentHashMap to select the segment that
    // a key is associated with. This avoids lock contention by ensuring that
    // the lock selected by this decorator parallels the one used by the data
    // store so that concurrent writes for different segments do not contend.
    concurrencyLevel = (builder.concurrencyLevel > MAXIMUM_SEGMENTS)
        ? MAXIMUM_SEGMENTS : builder.concurrencyLevel;
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
    data = new ConcurrentHashMap<K, Node>(builder.initialCapacity, 0.75f, concurrencyLevel);
    maximumWeightedSize = (builder.maximumWeightedCapacity > MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY
        : builder.maximumWeightedCapacity;
    resetMaximumHotWeightedSize();

    // The eviction support
    weigher = builder.weigher;
    sentinel = new Node();
    evictionLock = new ReentrantLock();
    writeQueue = new ConcurrentLinkedQueue<Runnable>();
    reorderQueueLength = new AtomicInteger[segments];
    reorderQueue = (Queue<Node>[]) new Queue[segments];
    for (int i=0; i<segments; i++) {
      segmentLock[i] = new ReentrantLock();
      reorderQueueLength[i] = new AtomicInteger();
      reorderQueue[i] = new ConcurrentLinkedQueue<Node>();
    }

    // The notification listener and event queue
    listener = builder.listener;
    listenerQueue = (listener == DiscardingListener.INSTANCE)
        ? (Queue<Node>) discardingQueue
        : new ConcurrentLinkedQueue<Node>();
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
   * Retrieves the maximum weighted capacity of the map.
   *
   * @return the maximum weighted capacity
   */
  public int capacity() {
    return maximumWeightedSize;
  }

  /**
   * Sets the maximum weighted capacity of the map and eagerly evicts entries
   * until it shrinks to the appropriate size.
   *
   * @param capacity the maximum weighted capacity of the map
   * @throws IllegalArgumentException if the capacity is negative
   */
  public void setCapacity(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException();
    }

    evictionLock.lock();
    try {
      maximumWeightedSize = capacity;
      resetMaximumHotWeightedSize();
      drainReorderQueues();
      drainWriteQueue();
      evict();
    } finally {
      evictionLock.unlock();
    }
  }

  /**
   * Sets the maximum number of entries that can be considered hot by the LIRS
   * page replacement algorithm based on the maximum weighted size.
   */
  @GuardedBy("evictionLock")
  private void resetMaximumHotWeightedSize() {
    // If the maximum hot size is equal to the maximum size then the page
    // replacement algorithm degrades to an LRU policy. To avoid this, the
    // maximum hot size is decreased by one when it would otherwise be equal.
    int size = (int) (HOT_RATE * maximumWeightedSize);
    maximumHotWeightedSize = (size == maximumWeightedSize)
        ? (maximumWeightedSize - 1) : size;
  }

  /**
   * Prunes HIR blocks in the bottom of the stack until an HOT block sits in
   * the stack bottom. If pruned blocks were resident then they remain in the
   * queue; otherwise they are no longer referenced and are evicted.
   */
  @GuardedBy("evictionLock")
  private void pruneStack() {
    // See section 3.3:
    // "We define an operation called "stack pruning" on the LIRS stack S,
    // which removes the HIR blocks in the bottom of the stack until an LIR
    // block sits in the stack bottom. This operation serves for two purposes:
    // (1) We ensure the block in the bottom of the stack always belongs to the
    // LIR block set. (2) After the LIR block in the bottom is removed, those
    // HIR blocks contiguously located above it will not have chances to change
    // their status from HIR to LIR, because their recencies are larger than
    // the new maximum recency of LIR blocks."
    Node bottom = sentinel.prevInStack;
    while ((bottom != sentinel) && (bottom.status != Status.HOT)) {
      if (bottom.status == Status.NON_RESIDENT) {
        // Notify the listener if the entry was evicted
        if (data.remove(bottom.key, bottom)) {
          listenerQueue.add(bottom);
        }
        weightedSize -= bottom.weightedValue.weight;
        bottom.remove();
      } else {
        bottom.removeFromStack();
      }
      bottom = sentinel.prevInStack;
    }
  }

  /**
   * Determines whether the map has exceeded its capacity.
   *
   * @return if the map has overflowed and an entry should be evicted
   */
  private boolean isOverflow() {
    return weightedSize > maximumWeightedSize;
  }

  /**
   * Evicts entries from the map while it exceeds the maximum weighted capacity
   * and appends evicted entries to the listener queue for processing.
   */
  @GuardedBy("evictionLock")
  private void evict() {
    // Attempts to evict entries from the map if it exceeds the maximum
    // capacity. If the eviction fails due to a concurrent removal of the
    // victim, that removal cancels out the addition that triggered this
    // eviction. The victim is eagerly unlinked before the removal task so
    // that if there are other pending prior additions then a new victim
    // will be chosen for removal.
    while (isOverflow()) {
      // See section 3.3 case #3:
      // "We remove the HIR resident block at the front of list Q (it then
      // becomes a non-resident block), and replace it out of the cache."

      //TODO(bmanes): Total hack. Fix or remove dynamic resizing.
      Node node = sentinel.nextInQueue;
      if (node == sentinel) {
        node = sentinel.nextInStack;
        if (node == sentinel) {
          return;
        }
      }

      // Notify the listener if the entry was evicted
      if (data.remove(node.key, node)) {
        listenerQueue.add(node);
      }
      weightedSize -= node.weightedValue.weight;
      node.remove();
    }
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
  private int addToReorderQueue(Node node) {
    int segment = node.segment;
    reorderQueue[segment].add(node);
    return reorderQueueLength[segment].incrementAndGet();
  }

  /**
   * Attempts to acquire the eviction lock and apply pending updates to the
   * eviction algorithm.
   *
   * @param segment the segment's reorder queue to drain
   * @param onlyIfWrites attempts to drain the eviction queues only if there
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
  private void attemptToDrainWriteQueue() {
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
   */
  @GuardedBy("evictionLock")
  void drainReorderQueues() {
    for (int segment=0; segment<segments; segment++) {
      drainReorderQueue(segment);
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
    // decremented by the delta rather than set to zero.
    int delta = 0;
    Node node;
    Queue<Node> queue = reorderQueue[segment];
    while ((node = queue.poll()) != null) {
      // skip the node if appended to reorder queue during its removal
      if (node.inStack() || node.inQueue()) {
        switch (node.status) {
          case HOT:
            node.hotHit();
            break;
          case COLD:
            node.coldHit();
            break;
          case NON_RESIDENT:
            node.miss();
            break;
          default:
            throw new AssertionError();
        }
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
    Node node;
    while ((node = listenerQueue.poll()) != null) {
      listener.onEviction(node.key, node.weightedValue.value);
    }
  }

  /**
   * Adds a node to the list and evicts an entry on overflow.
   */
  private final class AddTask implements Runnable {
    private final Node node;
    private final int weight;

    AddTask(Node node, int weight) {
      this.weight = weight;
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      weightedSize += weight;
      node.miss();
      evict();
    }
  }

  /**
   * Removes a node from the list.
   */
  private final class RemovalTask implements Runnable {
    private final Node node;

    RemovalTask(Node node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      if (node.inStack() || node.inQueue()) {
        weightedSize -= node.weightedValue.weight;
        node.remove();
      }
    }
  }

  /**
   * Updates the weighted size and evicts an entry on overflow.
   */
  private final class UpdateTask implements Runnable {
    private final int weightDifference;

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
    attemptToDrainWriteQueue();
    return data.isEmpty();
  }

  @Override
  public int size() {
    attemptToDrainWriteQueue();
    return data.size();
  }

  /**
   * Returns the weighted size of the map.
   *
   * @return the combined weight of the values in this map
   */
  public int weightedSize() {
    attemptToDrainWriteQueue();
    return weightedSize;
  }

  @Override
  public void clear() {
    // The alternative is to iterate through the keys and call #remove(), which
    // unnecessarily adds contention on the eviction lock and the write queue.
    // Instead the table is walked to conditionally remove the nodes and the
    // linkage fields are null'ed out to reduce GC pressure.
    evictionLock.lock();
    try {
      drainWriteQueue();

      for (Node node : data.values()) {
        node.prevInStack = node.nextInStack = null;
        node.prevInQueue = node.nextInQueue = null;
        weightedSize -= node.weightedValue.weight;
        data.remove(node.key, node);
      }
      sentinel.prevInStack = sentinel.nextInStack = sentinel;
      sentinel.prevInQueue = sentinel.nextInQueue = sentinel;

      drainReorderQueues();
    } finally {
      evictionLock.unlock();
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
    checkNotNull(value, "null value");
    attemptToDrainWriteQueue();

    for (Node node : data.values()) {
      if (node.weightedValue.value.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    checkNotNull(key, "null key");

    // As reads are the common case they should be performed lock-free to avoid
    // blocking other readers. If the entry was found then a reorder operation
    // is scheduled on the queue to be applied sometime in the future. The
    // draining of the queues should be delayed until either the reorder
    // threshold has been exceeded or if there is a pending write.
    int segment;
    V value = null;
    boolean delayReorder = true;
    Node node = data.get(key);
    if (node == null) {
      segment = segmentFor(key);
    } else {
      int buffered = addToReorderQueue(node);
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
   * Adds a node to the list and the data store. If an existing node is found,
   * then its value is updated if allowed.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @param onlyIfAbsent a write is performed only if the key is not already
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
    // treated as a read so that a reordering operation is scheduled.
    Node prior;
    V oldValue = null;
    int weightedDifference = 0;
    boolean delayReorder = true;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = new WeightedValue<V>(value, weight);
    Node node = new Node(key, weightedValue, segment);
    Runnable task = new AddTask(node, weight);

    // maintain per-segment write ordering
    lock.lock();
    try {
      prior = data.putIfAbsent(node.key, node);
      if (prior == null) {
        writeQueue.add(task);
      } else if (onlyIfAbsent) {
        oldValue = prior.weightedValue.value;
      } else {
        WeightedValue<V> oldWeightedValue = prior.weightedValue;
        weightedDifference = weight - oldWeightedValue.weight;
        prior.weightedValue = node.weightedValue;
        oldValue = oldWeightedValue.value;
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (prior != null) {
      if (weightedDifference != 0) {
        writeQueue.add(new UpdateTask(weightedDifference));
      }
      int buffered = addToReorderQueue(prior);
      delayReorder = (buffered <= REORDER_THRESHOLD);
    }
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
    Node node;
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
    Node node;
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
    Node node;
    int weightedDifference = 0;
    boolean delayReorder = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = new WeightedValue<V>(value, weight);

    // maintain per-segment write ordering
    lock.lock();
    try {
      node = data.get(key);
      if (node != null) {
        WeightedValue<V> oldWeightedValue = node.weightedValue;
        weightedDifference = weight - oldWeightedValue.weight;
        node.weightedValue = weightedValue;
        prior = oldWeightedValue.value;
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (node != null) {
      if (weightedDifference != 0) {
        writeQueue.add(new UpdateTask(weightedDifference));
      }
      int buffered = addToReorderQueue(node);
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
    Node node;
    boolean delayReorder = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(newValue);
    WeightedValue<V> oldWeightedValue = null;
    WeightedValue<V> newWeightedValue = new WeightedValue<V>(newValue, weight);

    // maintain per-segment write ordering
    lock.lock();
    try {
      node = data.get(key);
      if (node != null) {
        WeightedValue<V> weightedValue = node.weightedValue;
        if (oldValue.equals(weightedValue.value)) {
          node.weightedValue = newWeightedValue;
          oldWeightedValue = weightedValue;
        }
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (node != null) {
      if (oldWeightedValue != null) {
        writeQueue.add(new UpdateTask(newWeightedValue.weight - oldWeightedValue.weight));
      }
      int buffered = addToReorderQueue(node);
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
   * The page-replacement status of an entry.
   */
  enum Status {

    /**
     * A hot entry is a resident LIRS block that is held in the stack.
     */
    HOT,

    /**
     * A cold entry is a resident LIRS block that is held in the queue and may
     * be held in by stack.
     */
    COLD,

    /**
     * A non-resident HIRS entry is a block that may be held in the stack.
     */
    NON_RESIDENT
  }

  /**
   * A value and its weight.
   */
  static final class WeightedValue<V> {
    final int weight;
    final V value;

    public WeightedValue(V value, int weight) {
      if ((weight < 1) || (weight > MAXIMUM_WEIGHT)) {
        throw new IllegalArgumentException("invalid weight");
      }
      this.weight = weight;
      this.value = value;
    }
  }

  /**
   * An entry that contains the key, the weighted value, the segment index, and
   * linkage pointers on the page-replacement algorithm's data structures.
   */
  final class Node {
    final K key;
    @GuardedBy("segmentLock") // must write under lock
    volatile WeightedValue<V> weightedValue;

    /** The segment that the node is associated with. */
    final int segment;

    /** The page replacement status of the entry. */
    volatile Status status;

    /**
     * The links on the LIRS stack, S, which maintains recency information. The
     * stack holds all hot entries as well as the cold and non-resident entries
     * that are more recent than the least recent hot entry. The stack is
     * pruned so that the last entry is hot and all entries accessed more
     * recently than the last hot entry are present in the stack, The stack is
     * ordered by recency, with its most recently accessed entry at the top
     * and its least recently accessed entry at the bottom.
     */
    @GuardedBy("evictionLock")
    Node prevInStack;
    @GuardedBy("evictionLock")
    Node nextInStack;

    /**
     * The links on the LIRS queue, Q, which holds all cold entries and supplies
     * the victim entry at the head of the queue for eviction.
     */
    @GuardedBy("evictionLock")
    Node prevInQueue;
    @GuardedBy("evictionLock")
    Node nextInQueue;

    /** Creates a new sentinel node. */
    Node() {
      this.key = null;
      this.segment = -1;
      this.prevInStack = nextInStack = this;
      this.prevInQueue = nextInQueue = this;
    }

    /** Creates a new, unlinked node. */
    Node(K key, WeightedValue<V> weightedValue, int segment) {
      this.weightedValue = weightedValue;
      this.status = Status.NON_RESIDENT;
      this.segment = segment;
      this.key = key;
    }

    /* ---------------- Stack Support -------------- */

    /** Adds or moves the node to the top of the stack. */
    @GuardedBy("evictionLock")
    void moveToStack_top() {
      if (inStack()) {
        prevInStack.nextInStack = nextInStack;
        nextInStack.prevInStack = prevInStack;
      }
      addToStackBefore(sentinel.nextInStack);
    }

    /** Adds or moves the node to the bottom of the stack. */
    @GuardedBy("evictionLock")
    void moveToStack_bottom() {
      if (inStack()) {
        prevInStack.nextInStack = nextInStack;
        nextInStack.prevInStack = prevInStack;
      }
      addToStackBefore(sentinel);
    }

    /** Adds this node before the specified node in the stack. */
    @GuardedBy("evictionLock")
    void addToStackBefore(Node node) {
      prevInStack = node.prevInStack;
      nextInStack = node;
      prevInStack.nextInStack = this;
      nextInStack.prevInStack = this;
    }

    /** Removes this node from the stack. */
    @GuardedBy("evictionLock")
    void removeFromStack() {
      prevInStack.nextInStack = nextInStack;
      nextInStack.prevInStack = prevInStack;
      prevInStack = nextInStack = null;
    }

    /* ---------------- Queue Support -------------- */

    /** Adds or moves the node to the tail of the queue. */
    @GuardedBy("evictionLock")
    void moveToQueue_tail() {
      if (inQueue()) {
        prevInQueue.nextInQueue = nextInQueue;
        nextInQueue.prevInQueue = prevInQueue;
      }
      addToQueue();
    }

    /** Adds the node to the tail of the queue. */
    @GuardedBy("evictionLock")
    void addToQueue() {
      prevInQueue = sentinel.prevInQueue;
      nextInQueue = sentinel;
      prevInQueue.nextInQueue = this;
      nextInQueue.prevInQueue = this;
    }

    /** Removes the node from the queue. */
    @GuardedBy("evictionLock")
    void removeFromQueue() {

      prevInQueue.nextInQueue = nextInQueue;
      nextInQueue.prevInQueue = prevInQueue;
      prevInQueue = nextInQueue = null;
    }

    /** Whether the node is in the stack. */
    @GuardedBy("evictionLock")
    boolean inStack() {
      return (nextInStack != null);
    }

    /** Whether the node is in the queue. */
    @GuardedBy("evictionLock")
    boolean inQueue() {
      return (nextInQueue != null);
    }

    /* ---------------- Eviction Support -------------- */

    /**
     * Moves this entry from the queue to the stack, marking it hot (as cold
     * resident entries must remain in the queue).
     */
    @GuardedBy("evictionLock")
    private void migrateToStack(Node node) {
      node.removeFromQueue();
      if (!node.inStack()) {
        node.moveToStack_bottom();
      }
      makeHot();
    }

    /**
     * Moves this entry from the stack to the queue, marking it cold
     * (as hot entries must remain in the stack). This should only be called
     * on resident entries, as non-resident entries should not be made resident.
     * The bottom entry on the queue is always hot due to stack pruning.
     */
    @GuardedBy("evictionLock")
    private void migrateToQueue(Node node) {
      node.removeFromStack();
      makeCold();
    }

    /** Removes the node from the stack and queue. */
    @GuardedBy("evictionLock")
    void remove() {
      if (inQueue()) {
        removeFromQueue();
      }
      if (inStack()) {
        removeFromStack();
      }
    }

    /** Becomes a hot entry. */
    @GuardedBy("evictionLock")
    private void makeHot() {
      hotWeightedSize += weightedValue.weight;;
      status = Status.HOT;
    }

    /** Becomes a cold entry. */
    @GuardedBy("evictionLock")
    private void makeCold() {
      if (status == Status.HOT) {
        hotWeightedSize -= weightedValue.weight;
      }
      status = Status.COLD;
      moveToQueue_tail();
    }

    /** Becomes a non-resident entry. */
    @GuardedBy("evictionLock")
    private void makeNonResident() {
      int weight = weightedValue.weight;
      switch (status) {
        case HOT:
          weightedSize -= weight;
          hotWeightedSize -= weight;
          status = Status.NON_RESIDENT;
          break;
        case COLD:
          weightedSize -= weight;
          status = Status.NON_RESIDENT;
          break;
      }
    }

    /** Records a hit on a hot entry. */
    @GuardedBy("evictionLock")
    void hotHit() {
      // See section 3.3, case #1
      boolean onBottom = (sentinel.prevInStack == this);
      moveToStack_top();
      if (onBottom) {
        pruneStack();
      }
    }

    /** Records a hit on a cold block. */
    @GuardedBy("evictionLock")
    void coldHit() {
      // See section 3.3, case #2
      boolean inStack = inStack();
      moveToStack_top();
      if (!inStack) {
        moveToQueue_tail();
        return;
      }

      if (status != Status.HOT) {
        status = Status.HOT;
        hotWeightedSize += weightedValue.weight;
      }
      if (inQueue()) {
        removeFromQueue();
      }
      migrateToQueue(sentinel.prevInStack);
      pruneStack();
    }

    /**
     * Records a hit on a non-resident block. This is how entries join the LIRS
     * stack and queue.
     */
    @GuardedBy("evictionLock")
    void miss() {
      if (hotWeightedSize < maximumHotWeightedSize) {
        // When LIR block set is not full, all the referenced blocks are given
        // a hot status until the maximum hot weighted size is reached.
        makeHot();
        moveToStack_top();
        return;
      }

      evict();

      // Then we load the requested block X into the freed buffer and place it
      // on the top of stack.
      boolean inStack = inStack();
      moveToStack_top();
      if (inStack) {
        // If the node is in stack, we change its status to LIR and move the
        // LIR block in the bottom of the stack to the end of list with its
        // status changed to HIR. A stack pruning is then conducted.
        makeHot();
        migrateToQueue(sentinel.prevInStack);
        pruneStack();
      } else {
        // If the node is not in the stack, we leave its status in HIR and
        // place it at the end of queue.
        makeCold();
      }
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
      Node node = map.data.get(entry.getKey());
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
    private final Iterator<Node> iterator;
    private Node current;

    public EntryIterator(Iterator<Node> iterator) {
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
  private final class WriteThroughEntry extends SimpleEntry<K, V> {
    private static final long serialVersionUID = 1;

    public WriteThroughEntry(Node node) {
      super(node.key, node.weightedValue.value);
    }

    @Override
    public V setValue(V value) {
      put(getKey(), value);
      return super.setValue(value);
    }

    private Object writeReplace() {
      return new SimpleEntry<K, V>(this);
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
  enum DiscardingListener implements EvictionListener {
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
      data = new HashMap<K, V>(map);
      listener = map.listener;
      capacity = map.maximumWeightedSize;
      weigher = map.weigher;
    }

    private Object readResolve() {
      ConcurrentLinkedHashMap<K, V> map = new Builder<K, V>()
          .concurrencyLevel(concurrencyLevel)
          .maximumWeightedCapacity(capacity)
          .listener(listener)
          .weigher(weigher)
          .build();
      map.putAll(data);
      return map;
    }

    private static final long serialVersionUID = 1;
  }

  /* ---------------- Builder -------------- */

  /**
   * A builder that creates {@link ConcurrentLinkedHashMap} instances. It
   * provides a flexible approach for constructing customized instances with
   * a named parameter syntax. It can be used in the following manner:
   * <p>
   * <pre>
   * {@code
   *   // a cache of the groups that a user belongs to
   *   ConcurrentMap<User, Set<Group>> groups = new Builder<User, Set<Group>>()
   *       .weigher(Weighers.<Group>set())
   *       .maximumWeightedCapacity(5000)
   *       .build();
   * }
   * </pre>
   */
  @SuppressWarnings("unchecked")
  public static final class Builder<K, V> {
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    EvictionListener<K, V> listener;
    int maximumWeightedCapacity;
    int concurrencyLevel;
    int initialCapacity;
    Weigher<V> weigher;

    public Builder() {
      maximumWeightedCapacity = -1;
      weigher = Weighers.singleton();
      initialCapacity = DEFAULT_INITIAL_CAPACITY;
      concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;
      listener = (EvictionListener<K, V>) DiscardingListener.INSTANCE;
    }

    /**
     * Specifies the initial capacity of the hash table (default <tt>16</tt>).
     * This is the number of key-value pairs that the hash table can hold
     * before a resize operation is required.
     *
     * @param initialCapacity the initial capacity used to size the hash table
     *     to accommodate this many entries.
     * @throws IllegalArgumentException if the initialCapacity is negative
     */
    public Builder<K, V> initialCapacity(int initialCapacity) {
      if (initialCapacity < 0) {
        throw new IllegalArgumentException();
      }
      this.initialCapacity = initialCapacity;
      return this;
    }

    /**
     * Specifies the maximum weighted capacity to coerces the map to and may
     * exceed it temporarily.
     *
     * @param maximumWeightedCapacity the weighted threshold to bound the map
     *     by
     * @throws IllegalArgumentException if the maximumWeightedCapacity is
     *     negative
     */
    public Builder<K, V> maximumWeightedCapacity(int maximumWeightedCapacity) {
      if (maximumWeightedCapacity < 0) {
        throw new IllegalArgumentException();
      }
      this.maximumWeightedCapacity = maximumWeightedCapacity;
      return this;
    }

    /**
     * Specifies the estimated number of concurrently updating threads. The
     * implementation performs internal sizing to try to accommodate this many
     * threads (default <tt>16</tt>).
     *
     * @param concurrencyLevel the estimated number of concurrently updating
     *     threads
     * @throws IllegalArgumentException if the concurrencyLevel is less than or
     *     equal to zero
     */
    public Builder<K, V> concurrencyLevel(int concurrencyLevel) {
      if (concurrencyLevel <= 0) {
        throw new IllegalArgumentException();
      }
      this.concurrencyLevel = concurrencyLevel;
      return this;
    }

    /**
     * Specifies an optional listener that is registered for notification when
     * an entry is evicted.
     *
     * @param listener the object to forward evicted entries to
     * @throws IllegalArgumentException if the listener is null
     */
    public Builder<K, V> listener(EvictionListener<K, V> listener) {
      if (listener == null) {
        throw new IllegalArgumentException();
      }
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
      if (weigher == null) {
        throw new IllegalArgumentException();
      }
      this.weigher = weigher;
      return this;
    }

    /**
     * Creates a new {@link ConcurrentLinkedHashMap} instance.
     *
     * @throws IllegalArgumentException if the maximum weighted capacity was
     *     not set
     */
    public ConcurrentLinkedHashMap<K, V> build() {
      maximumWeightedCapacity(maximumWeightedCapacity);
      return new ConcurrentLinkedHashMap<K, V>(this);
    }
  }
}
