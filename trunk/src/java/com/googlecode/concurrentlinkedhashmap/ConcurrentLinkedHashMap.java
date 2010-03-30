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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.CLASS;
import java.lang.annotation.Target;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
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
 * @author <a href="mailto:ben.manes@gmail.com">Ben Manes</a>
 * @see <tt>http://code.google.com/p/concurrentlinkedhashmap/</tt>
 */
public final class ConcurrentLinkedHashMap<K, V> extends AbstractMap<K, V>
    implements ConcurrentMap<K, V> {
  /**
   * Number of cache reorder operations that can be buffered per segment before
   * the cache's ordering information is updated. This is used to avoid lock
   * contention by recording a memento on reads and delaying a lock acquisition
   * until the threshold is crossed or a mutation occurs.
   */
  static final int REORDER_THRESHOLD = 64;

  /** The maximum number of segments to allow. */
  static final int MAX_SEGMENTS = 1 << 16; // slightly conservative

  /** A queue that discards all entries. */
  static final Queue nullQueue = new DiscardingQueue();

  /** The backing data store holding the key-value associations. */
  final ConcurrentMap<K, Node<K, V>> data;

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
  volatile int length;
  @GuardedBy("evictionLock")
  final Node<K, V> sentinel;

  volatile int capacity;
  final Lock evictionLock;
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
    int concurrencyLevel = (builder.concurrencyLevel > MAX_SEGMENTS)
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
    listenerQueue = (listener == Builder.nullListener)
        ? (Queue<Node<K, V>>) nullQueue
        : new ConcurrentLinkedQueue<Node<K, V>>();
  }

  /**
   * Creates a builder for constructing the map, such as:
   * <p>
   * {@code
   *   Builder<K, V> builder = ConcurrentLinkedHashMap.builder();
   *   // configure builder
   *   ConcurrentMap<K, V> map = builder.build();
   * }
   */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<K, V>();
  }

  /**
   * Asserts that the object is not null.
   */
  private static void checkNotNull(Object o, String message) {
    if (o == null) {
      throw new NullPointerException(message);
    }
  }

  /* ---------------- Eviction support -------------- */

  /**
   * Retrieves the maximum capacity of the map.
   *
   * @return The maximum capacity
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
      while (isOverflow()) {
        evict();
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
    return length > capacity;
  }

  /**
   * Tries to evicts an entry from the map if it exceeds the maximum capacity.
   * If the eviction fails due to a concurrent removal of the victim, that
   * removal cancels out the addition that triggered the eviction. The victim
   * is eagerly unlinked before the removal task so that if their is a pending
   * prior addition a new victim can be chosen.
   */
  @GuardedBy("evictionLock")
  private void evict() {
    if (isOverflow()) {
      Node<K, V> node = sentinel.next;
      // Notify the listener if the entry was evicted
      if (data.remove(node.key, node)) {
        listenerQueue.add(node);
      }
      node.remove();
      length--;
    }
  }

  /**
   * Determines the segment that the key is associated to. To avoid lock
   * contention this should always parallel the segment selected by
   * {@link ConcurrentHashMap} so that concurrent writes for different
   * segments do not contend.
   */
  int segmentFor(Object key) {
    int hash = hash(key.hashCode());
    return (hash >>> segmentShift) & segmentMask;
  }

  /**
   * Applies a supplemental hash function to a given hashCode, which
   * defends against poor quality hash functions.  This is critical
   * because ConcurrentHashMap uses power-of-two length hash tables,
   * that otherwise encounter collisions for hashCodes that do not
   * differ in lower or upper bits.
   */
  private static int hash(int h) {
    // Spread bits to regularize both segment and index locations,
    // using variant of single-word Wang/Jenkins hash.
    h += (h <<  15) ^ 0xffffcd7d;
    h ^= (h >>> 10);
    h += (h <<   3);
    h ^= (h >>>  6);
    h += (h <<   2) + (h << 14);
    return h ^ (h >>> 16);
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
   * eviction algorithm. This is attempted only if there is a pending write
   * or the segment's reorder buffer has exceeded the threshold.
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
    Queue<Node<K, V>> queue = reorderQueue[segment];
    Node<K, V> node;
    int delta = 0;

    while ((node = queue.poll()) != null) {
      // skip the node if appended to queue during its removal
      if (node.isLinked()) {
        node.moveToTail();
        delta--;
      }
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
   * Notifies the listeners that the entry was evicted.
   */
  private void notifyListeners() {
    Node<K, V> node;
    while ((node = listenerQueue.poll()) != null) {
      listener.onEviction(node.key, node.value);
    }
  }

  /**
   * Adds a node to the list and evicts an entry on overflow.
   */
  private final class AddTask implements Runnable {
    private final Node<K, V> node;

    AddTask(Node<K, V> node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      node.appendToTail();
      length++;
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
        node.remove();
        length--;
      }
    }
  }

  /* ---------------- Concurrent Map support -------------- */

  @Override
  public int size() {
    attemptToDrainWriteQueues();
    return length;
  }

  @Override
  public void clear() {
    if ((length == 0) && writeQueue.isEmpty()) {
      return;
    }

    List<Node<K, V>> nodes;
    evictionLock.lock();
    try {
      drainWriteQueue();
      nodes = new ArrayList<Node<K, V>>(length);
      length = 0;

      Node<K, V> node = sentinel.next;
      while (node != sentinel) {
        nodes.add(node);
        node.prev.next = null;
        node.prev = null;
        node = node.next;
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
      if (node.value.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    checkNotNull(key, "null key");

    int segment;
    V value = null;
    boolean delayReorder = true;
    Node<K, V> node = data.get(key);
    if (node == null) {
      segment = segmentFor(key);
    } else {
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
      segment = node.segment;
      value = node.value;
    }
    processEvents(segment, delayReorder);
    return value;
  }

  @Override
  public V put(K key, V value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    V oldValue = null;
    boolean delayReorder = true;
    int segment = segmentFor(key);
    Node<K, V> previous = putIfAbsent(new Node<K, V>(key, value, segment, sentinel));
    if (previous != null) {
      delayReorder = false;
      oldValue = previous.getAndSetValue(value);
    }
    processEvents(segment, delayReorder);
    return oldValue;
  }

  @Override
  public V putIfAbsent(K key, V value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    V oldValue = null;
    boolean delayReorder = true;
    int segment = segmentFor(key);
    Node<K, V> previous = putIfAbsent(new Node<K, V>(key, value, segment, sentinel));
    if (previous != null) {
      delayReorder = false;
      oldValue = previous.value;
    }
    processEvents(segment, delayReorder);
    return oldValue;
  }

  /**
   * Adds a node to the list and data store if it does not already exist.
   *
   * @param node an unlinked node to add
   * @return the previous value in the data store
   */
  private Node<K, V> putIfAbsent(Node<K, V> node) {
    Lock lock = segmentLock[node.segment];
    Runnable task = new AddTask(node);
    Node<K, V> previous;

    // maintain per-segment write ordering
    lock.lock();
    try {
      previous = data.putIfAbsent(node.key, node);
      if (previous == null) {
        writeQueue.add(task);
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    if (previous != null) {
      appendToReorderQueue(previous);
    }
    return previous;
  }

  @Override
  public V remove(Object key) {
    checkNotNull(key, "null key");

    V value = null;
    Node<K, V> node;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];

    // maintain per-segment write ordering
    lock.lock();
    try {
      node = data.remove(key);
      if (node != null) {
        value = node.value;
        writeQueue.add(new RemovalTask(node));
      }
    } finally {
      lock.unlock();
    }

    // perform outside of lock
    processEvents(segment, true);
    return value;
  }

  @Override
  public boolean remove(Object key, Object value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    Node<K, V> node;
    boolean removed = false;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];

    // maintain per-segment write ordering
    lock.lock();
    try {
      node = data.get(key);
      if ((node != null) && node.value.equals(value)) {
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

    int segment;
    V previous = null;
    boolean delayReorder = false;
    Node<K, V> node = data.get(key);
    if (node == null) {
      segment = segmentFor(key);
    } else {
      previous = node.getAndSetValue(value);
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
      segment = node.segment;
    }
    processEvents(segment, delayReorder);
    return previous;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkNotNull(key, "null key");
    checkNotNull(oldValue, "null oldValue");
    checkNotNull(newValue, "null newValue");

    int segment;
    boolean replaced = false;
    boolean delayReorder = false;
    Node<K, V> node = data.get(key);
    if (node == null) {
      segment = segmentFor(key);
    } else {
      replaced = node.casValue(oldValue, newValue);
      int buffered = appendToReorderQueue(node);
      delayReorder = (buffered <= REORDER_THRESHOLD);
      segment = node.segment;
    }
    processEvents(segment, delayReorder);
    return replaced;
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
   * A listener registered for notification when an entry is evicted.
   */
  public interface EvictionListener<K, V> {

    /**
     * A call-back notification that the entry was evicted.
     *
     * @param key the evicted key
     * @param value the evicted value
     */
    void onEviction(K key, V value);
  }

  /**
   * A node on the double-linked list. This list cross-cuts the data store.
   */
  @SuppressWarnings("unchecked")
  static final class Node<K, V> {
    private static final AtomicReferenceFieldUpdater<Node, Object> valueUpdater =
        AtomicReferenceFieldUpdater.newUpdater(Node.class, Object.class, "value");

    final Node<K, V> sentinel;
    Node<K, V> prev;
    Node<K, V> next;

    final K key;
    volatile V value;
    final int segment;

    /**
     * Creates a new sentinel node.
     */
    public Node() {
      this.sentinel = this;
      this.segment = -1;
      this.value = null;
      this.key = null;
      this.prev = this;
      this.next = this;
    }

    /**
     * Creates a new, unlinked node.
     */
    public Node(K key, V value, int segment, Node<K, V> sentinel) {
      this.sentinel = sentinel;
      this.segment = segment;
      this.value = value;
      this.key = key;
      this.prev = null;
      this.next = null;
    }

    /**
     * Atomically retrieves and updates the value.
     */
    public V getAndSetValue(V value) {
      return (V) valueUpdater.getAndSet(this, value);
    }

    /**
     * Atomically updates the value if its equal to the expected value.
     */
    public boolean casValue(V expect, V update) {
      return valueUpdater.compareAndSet(this, expect, update);
    }

    /**
     * Removes the node from the list.
     */
    public void remove() {
      prev.next = next;
      next.prev = prev;
      prev = next = null;
    }

    /**
     * Appends the node to the tail of the list.
     */
    public void appendToTail() {
      prev = sentinel.prev;
      next = sentinel;
      sentinel.prev.next = this;
      sentinel.prev = this;
    }

    /**
     * Moves the node to the tail of the list.
     */
    public void moveToTail() {
      if (next != sentinel) {
        prev.next = next;
        next.prev = prev;
        appendToTail();
      }
    }

    /**
     * Whether the node is linked on the list.
     */
    public boolean isLinked() {
      return (next != null);
    }

    @Override
    public String toString() {
      return key + "=" + value;
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
   * An adapter to safely externalize the keys.
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
   * An adapter to represent the data store's values in the external type.
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
   * An adapter to represent the data store's values in the external type.
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
   * An adapter to represent the data store's entry set in the external type.
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
      return (node != null) && (node.value.equals(entry.getValue()));
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
   * An adapter to represent the data store's entry iterator in the external
   * type.
   */
  private final class EntryIterator implements Iterator<Entry<K, V>> {
    private final Iterator<Node<K, V>> iterator;
    private Entry<K, V> current;

    public EntryIterator(Iterator<Node<K, V>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Entry<K, V> next() {
      current = new WriteThroughEntry(iterator.next());
      return current;
    }

    @Override
    public void remove() {
      if (current == null) {
        throw new IllegalStateException();
      }
      ConcurrentLinkedHashMap.this.remove(current.getKey(), current.getValue());
      current = null;
    }
  }

  /**
   * An entry that is tied to the map instance to allow updates through the
   * entry or the map to be visible.
   */
  private final class WriteThroughEntry implements Entry<K, V> {
    private final Node<K, V> node;

    public WriteThroughEntry(Node<K, V> node) {
      this.node = node;
    }

    @Override
    public K getKey() {
      return node.key;
    }

    @Override
    public V getValue() {
      return node.value;
    }

    @Override
    public V setValue(V value) {
      return replace(getKey(), value);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      } else if (!(obj instanceof Entry)) {
        return false;
      }
      Entry<?, ?> entry = (Entry<?, ?>) obj;
      return eq(getKey(), entry.getKey()) && eq(getValue(), entry.getValue());
    }

    @Override
    public int hashCode() {
      K key = getKey();
      V value = getValue();
      return ((key == null) ? 0 : key.hashCode()) ^
             ((value == null) ? 0 : value.hashCode());
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }

    private boolean eq(Object o1, Object o2) {
      return (o1 == o2) || ((o1 != null) && o1.equals(o2));
    }
  }

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
   * The field or method to which this annotation is applied can only be
   * accessed when holding a particular lock, which may be a built-in
   * (synchronization) lock, or may be an explicit {@link Lock}.
   *
   * @see <tt>http://www.jcip.net</tt>
   */
  @Retention(CLASS)
  @Target({FIELD, METHOD})
  private @interface GuardedBy {
      String value();
  }

  /**
   * A builder that creates {@link ConcurrentLinkedHashMap} instances.
   */
  @SuppressWarnings("unchecked")
  public static final class Builder<K, V> {
    private static final EvictionListener<?, ?> nullListener =
        new EvictionListener<Object, Object>() {
          @Override
          public void onEviction(Object key, Object value) {}
        };
    private int maximumCapacity;
    private int concurrencyLevel = 16;
    private EvictionListener<K, V> listener;

    public Builder() {
      maximumCapacity = -1;
      concurrencyLevel = 16;
      listener = (EvictionListener<K, V>) nullListener;
    }

    /**
     * Specifies the maximum capacity to coerces to. The size may exceed it
     * temporarily.
     */
    public Builder<K, V> maximumCapacity(int maximumCapacity) {
      this.maximumCapacity = maximumCapacity;
      return this;
    }

    /**
     * Specifies the estimated number of concurrently updating threads. The
     * implementation performs internal sizing to try to accommodate this many
     * threads (default <tt>16</tt>).
     */
    public Builder<K, V> concurrencyLevel(int concurrencyLevel) {
      this.concurrencyLevel = concurrencyLevel;
      return this;
    }

    /**
     * Specifies an optional listener registered for notification when an entry
     * is evicted.
     */
    public Builder<K, V> listener(EvictionListener<K, V> listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Creates a new {@link ConcurrentLinkedHashMap} instance.
     *
     * @throws IllegalArgumentException if the maximum capacity is less than
     *     zero, the concurrency level is not positive, or the listener is
     *     <tt>null</tt> if specified
     */
    public ConcurrentLinkedHashMap<K, V> build() {
      if ((maximumCapacity < 0) || (concurrencyLevel <= 0) || (listener == null)) {
        throw new IllegalArgumentException();
      }
      return new ConcurrentLinkedHashMap<K, V>(this);
    }
  }
}