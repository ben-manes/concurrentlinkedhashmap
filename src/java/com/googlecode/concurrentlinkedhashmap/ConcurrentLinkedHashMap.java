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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
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
import java.util.concurrent.atomic.AtomicIntegerArray;
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
 * a threshold determined by a {@link CapacityLimiter}. The default limiter
 * bounds the map by its <tt>maximum weighted capacity</tt>. A {@link Weigher}
 * instance determines how many units of capacity that a value consumes. The
 * default weigher assigns each value a weight of <tt>1</tt> to bound the map by
 * the total number of key-value pairs. A map that holds collections may choose
 * to weigh values by the number of elements in the collection and bound the map
 * by the total number of elements that it contains. A change to a value that
 * modifies its weight requires that an update operation is performed on the
 * map.
 * <p>
 * An {@link EvictionListener} may be supplied for notification when an entry
 * is evicted from the map. This listener is invoked on a caller's thread and
 * will not block other threads from operating on the map. An implementation
 * should be aware that the caller's thread will not expect long execution
 * times or failures as a side effect of the listener being notified. Execution
 * safety and a fast turn around time can be achieved by performing the
 * operation asynchronously, such as by submitting a task to an
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
 * does <em>not</em> allow <tt>null</tt> to be used as a key or value. Unlike
 * {@link java.util.LinkedHashMap}, this class does <em>not</em> provide
 * predictable iteration order.
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
  // This implementation uses a global write queue and multiple read queues to
  // record a memento of the the additions, removals, and accesses that were
  // performed on the map. The write queue is drained at the first opportunity
  // and the read queues are drained after a write or when a queue exceeds its
  // capacity threshold. A strict recency ordering is achieved by observing that
  // each read queue is in a weakly sorted order, starting from the last drain.
  // The read queues can be merged in O(n) time so that the recency operations
  // are applied in the expected order.
  //
  // The page replacement algorithm is chosen based on whether the singleton
  // weigher is used. If so, the Low Inter-reference Recency Set (LIRS) policy
  // is enabled. Otherwise the Least Recently Used (LRU) policy is used. This
  // avoids a race condition when the value's weight changes while it is being
  // moved between the LIRS stack and queue.
  //
  // The Low Inter-reference Recency Set (LIRS) was chosen as the default page
  // replacement algorithm due to its high hit rate, ability to track both the
  // recency and frequency, and be implemented with O(1) time complexity. This
  // algorithm is described in [1] and was evaluated against other algorithms in
  // [2].
  //
  // The Least Recently Used (LRU) page replacement algorithm was chosen for
  // weighted values due to its simplicity, high hit rate, and ability to be
  // implemented with O(1) time complexity.
  //
  // [1] LIRS: An Efficient Low Inter-reference Recency Set Replacement to
  // Improve Buffer Cache Performance
  // [2] The Performance Impact of Kernel Prefetching on Buffer Cache
  // Replacement Algorithms

  /**
   * Number of cache access operations that can be buffered per recency queue
   * before the cache's recency ordering information is updated. This is used
   * to avoid lock contention by recording a memento of reads and delaying a
   * lock acquisition until the threshold is crossed or a mutation occurs.
   */
  static final int RECENCY_THRESHOLD = 16;

  /** The maximum number of segments to allow. */
  static final int MAXIMUM_SEGMENTS = 1 << 16; // slightly conservative

  /** The maximum weighted capacity of the map. */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /** The maximum weight of a value. */
  static final int MAXIMUM_WEIGHT = 1 << 29;

  /** The percentage of the cache which is dedicated to hot blocks. */
  static final float HOT_RATE = 0.99f;

  /** A queue that discards all entries. */
  static final Queue<?> discardingQueue = new DiscardingQueue<Object>();

  /** The backing data store holding the key-value associations. */
  final ConcurrentMap<K, Node> data;
  final int concurrencyLevel;

  // These fields mirror the lock striping on ConcurrentHashMap to order
  // the write operations. This allows the write queue to be consistent.
  final int segmentMask;
  final int segmentShift;
  final Lock[] segmentLock;

  // These fields provide support to bound the map by a maximum capacity.
  @GuardedBy("evictionLock")
  final Node sentinel;

  volatile int globalRecencyOrder;
  final int maxRecenciesToDrain;
  @GuardedBy("evictionLock")
  int drainedRecencyOrder;

  final Policy policy;
  final Lock evictionLock;
  final Weigher<? super V> weigher;
  final Queue<Runnable> writeQueue;
  final AtomicIntegerArray recencyQueueLength;
  final Queue<RecencyReference>[] recencyQueue;

  // These fields provide support for notifying a listener.
  final Queue<Node> listenerQueue;
  final EvictionListener<K, V> listener;

  transient Set<K> keySet;
  transient Collection<V> values;
  transient Set<Map.Entry<K,V>> entrySet;

  /**
   * Creates an instance based on the builder's configuration.
   */
  @SuppressWarnings({"unchecked", "cast"})
  private ConcurrentLinkedHashMap(Builder<K, V> builder) {
    // The shift and mask used by ConcurrentHashMap to select the segment that
    // a key is associated with. This avoids lock contention by ensuring that
    // the lock selected by this decorator parallels the one used by the data
    // store so that concurrent writes for different segments do not contend.
    concurrencyLevel = Math.min(builder.concurrencyLevel, MAXIMUM_SEGMENTS);
    int sshift = 0;
    int segments = 1;
    while (segments < concurrencyLevel) {
      ++sshift;
      segments <<= 1;
    }
    segmentShift = 32 - sshift;
    segmentMask = segments - 1;
    segmentLock = new Lock[segments];
    for (int i = 0; i < segments; i++) {
      segmentLock[i] = new ReentrantLock();
    }
    data = new ConcurrentHashMap<K, Node>(builder.initialCapacity, 0.75f, concurrencyLevel);

    // The eviction support
    weigher = builder.weigher;
    policy = (weigher == Weighers.singleton()) ? new LirsPolicy() : new LruPolicy();
    sentinel = policy.createSentinel();
    evictionLock = new ReentrantLock();
    writeQueue = new ConcurrentLinkedQueue<Runnable>();
    policy.setCapacity(builder.maximumWeightedCapacity);

    recencyQueue = (Queue<RecencyReference>[]) new Queue[segments];
    recencyQueueLength = new AtomicIntegerArray(recencyQueue.length);
    maxRecenciesToDrain = (1 + recencyQueue.length) * RECENCY_THRESHOLD;
    for (int i = 0; i < recencyQueue.length; i++) {
      recencyQueue[i] = new ConcurrentLinkedQueue<RecencyReference>();
    }

    // The notification listener and event queue
    listener = builder.listener;
    listenerQueue = (listener == DiscardingListener.INSTANCE)
        ? (Queue<Node>) discardingQueue
        : new ConcurrentLinkedQueue<Node>();
  }

  /** Asserts that the object is not null. */
  static void checkNotNull(Object o, String message) {
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
    return policy.capacity();
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
      policy.setCapacity(capacity);
      drainRecencyQueues();
      drainWriteQueue();
      evict();
    } finally {
      evictionLock.unlock();
    }
    notifyListener();
  }

  /**
   * Evicts entries from the map while it exceeds the capacity and appends
   * evicted entries to the listener queue for processing.
   *
   * @param capacityLimiter the algorithm to determine whether to evict an entry
   */
  @GuardedBy("evictionLock")
  void evict() {
    // Attempts to evict entries from the map if it exceeds the maximum
    // capacity. If the eviction fails due to a concurrent removal of the
    // victim, that removal may cancel out the addition that triggered this
    // eviction. The victim is eagerly unlinked before the removal task so
    // that if an eviction is still required then a new victim will be chosen
    // for removal.
    while (policy.hasOverflowed()) {
      Node node = policy.victim();
      if (node == sentinel) {
        // The map has evicted all of its entries and can offer no further aid
        // in fulfilling the limiter's constraint. Note that for the weighted
        // capacity limiter, pending operations will adjust the size to reflect
        // the correct weight.
        return;
      }

      // Notify the listener if the entry was evicted
      if (data.remove(node.key, node)) {
        listenerQueue.add(node);
      }
      policy.evict(node);
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
    int hash = spread(key.hashCode());
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
  static int spread(int hashCode) {
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
   * Adds the entry to the recency queue for a future update to the page
   * replacement algorithm. An entry should be added to the queue when it is
   * accessed on either a read or update operation. This aids the page
   * replacement algorithm in choosing the best victim when an eviction is
   * required.
   *
   * @param node the entry that was accessed
   * @return true if the size does not exceeds the threshold
   */
  boolean addToRecencyQueue(Node node) {
    // A recency queue is chosen by the thread's id so that recencies are evenly
    // distributed between queues. This ensures that hot entries do not cause
    // contention due to the threads trying to append to the same queue.
    int index = (int) Thread.currentThread().getId() & segmentMask;

    // The recency's global order is acquired in a racy fashion as the increment
    // is not atomic with the insertion. This means that concurrent reads can
    // have the same ordering and the queues are in a weakly sorted order.
    int recencyOrder = globalRecencyOrder++;

    recencyQueue[index].add(new RecencyReference(node, recencyOrder));
    int buffered = recencyQueueLength.incrementAndGet(index);
    return (buffered <= RECENCY_THRESHOLD);
  }

  /**
   * Attempts to acquire the eviction lock and apply pending updates to the
   * eviction algorithm.
   *
   * @param onlyIfWrites attempts to drain the eviction queues only if there
   *     are pending writes
   */
  void tryToDrainEvictionQueues(boolean onlyIfWrites) {
    if (onlyIfWrites && writeQueue.isEmpty()) {
      return;
    }
    if (evictionLock.tryLock()) {
      try {
        drainRecencyQueues();
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
   * Drains the recency queues and applies the pending reorderings.
   */
  @GuardedBy("evictionLock")
  void drainRecencyQueues() {
    // A strict recency ordering is achieved by observing that each queue
    // contains recencies in weakly sorted order starting from the last drain.
    // The queues can be merged into a sorted array in O(n) time by using
    // counting sort and chaining on a collision.

    // The merged output is capped to the expected number of recencies plus
    // additional slack to optimistically handle the concurrent additions to
    // the queues.
    Object[] recencies = new Object[maxRecenciesToDrain];

    // The overflow buffer contains the recencies that were polled from a queue,
    // but that exceeded the capacity of the output array. The draining from the
    // queue should halt when this occurs and these recencies are applied last.
    List<RecencyReference> overflow = new ArrayList<RecencyReference>(recencyQueue.length);

    // Moves the recencies into the output collections, applies the reorderings,
    // and updates the marker for the starting recency order of the next drain.
    int lastRecencyIndex = moveRecenciesFromQueues(recencies, overflow);
    applyRecencyReorderings(recencies, lastRecencyIndex, overflow);
    updateDrainedRecencyOrder(recencies, lastRecencyIndex);
  }

  /**
   * Moves the recencies from the recency queues into the output collections.
   *
   * @param recencies the ordered array of the pending recency operations
   * @param overflow the recencies to apply last due to overflowing the array
   * @return the highest index location of a recency that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveRecenciesFromQueues(Object[] recencies, List<RecencyReference> overflow) {
    int lastRecencyIndex = -1;
    for (int i = 0; i < recencyQueue.length; i++) {
      int lastIndex = moveRecenciesFromQueue(i, recencies, overflow);
      lastRecencyIndex = Math.max(lastIndex, lastRecencyIndex);
    }
    return lastRecencyIndex;
  }

  /**
   * Moves the recencies from the specified queue into the output collections.
   *
   * @param queueIndex the recency queue to drain
   * @param recencies the ordered array of the pending recency operations
   * @param overflow the recencies to apply last due to overflowing the array
   * @return the highest index location of a recency that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveRecenciesFromQueue(int queueIndex, Object[] recencies, List<RecencyReference> overflow) {
    // While a queue is being drained it may be concurrently appended to. The
    // number of elements removed are tracked so that the length can be
    // decremented by the delta rather than set to zero.
    Queue<RecencyReference> queue = recencyQueue[queueIndex];
    int removedFromQueue = 0;

    int maxIndex = -1;
    RecencyReference recency;
    while ((recency = queue.poll()) != null) {
      removedFromQueue++;

      // The index into the output array is determined by calculating the offset
      // since the last drain.
      int index = recency.recencyOrder - drainedRecencyOrder;
      if (index < 0) {
        // The recency was missed by the last drain (due to the queues being
        // weakly sorted) and can be applied immediately.
        reorderRecency(recency);
        continue;
      } else if (index >= recencies.length) {
        // Due to concurrent additions, the recency order exceeds the capacity
        // of the output array. It is added to the overflow list and remaining
        // recencies in the queue will be handled by the next drain.
        overflow.add(recency);
        break;
      } else {
        // Add the recency as the head of the chain at the index location
        recency.next = (RecencyReference) recencies[index];
        recencies[index] = recency;
      }
      maxIndex = Math.max(index, maxIndex);
    }
    recencyQueueLength.addAndGet(queueIndex, -removedFromQueue);
    return maxIndex;
  }

  /**
   * Applies the pending recency reorderings to the page replacement policy.
   *
   * @param recencies the ordered array of the pending recency operations
   * @param lastRecencyIndex the last index of the recencies array
   * @param overflow the recencies to apply last
   */
  @GuardedBy("evictionLock")
  void applyRecencyReorderings(Object[] recencies, int lastRecencyIndex,
      List<RecencyReference> overflow) {
    for (int i = 0; i <= lastRecencyIndex; i++) {
      reorderRecencyiesInChain((RecencyReference) recencies[i]);
    }
    for (int i = 0; i < overflow.size(); i++) {
      reorderRecency(overflow.get(i));
    }
  }

  /**
   * Applies the pending recency operations for the recencies on the linked
   * chain.
   *
   * @param head the first recency in the chain of recency operations
   */
  @GuardedBy("evictionLock")
  void reorderRecencyiesInChain(RecencyReference head) {
    RecencyReference recency = head;
    while (recency != null) {
      reorderRecency(recency);
      recency = recency.next;
    }
  }

  /**
   * Applies the pending recency reorderings to the page replacement policy.
   *
   * @param recency the pending recency operation
   */
  @GuardedBy("evictionLock")
  void reorderRecency(RecencyReference recency) {
    // An entry may scheduled for reordering despite having been previously
    // removed. This can occur when the entry was concurrently read while a
    // writer was removing it. If the entry was garbage collected or no longer
    // linked then it does not need to be processed.
    Node node = recency.get();
    if ((node != null) && node.isLinked()) {
      node.reorder();
    }
  }

  /**
   * Updates the recency order to start the next drain from.
   *
   * @param recencies the ordered array of the recency operations
   * @param lastRecencyIndex the last index of the recencies array
   */
  @GuardedBy("evictionLock")
  void updateDrainedRecencyOrder(Object[] recencies, int lastRecencyIndex) {
    if (lastRecencyIndex >= 0) {
      RecencyReference recency = (RecencyReference) recencies[lastRecencyIndex];
      drainedRecencyOrder = recency.recencyOrder + 1;
    }
  }

  /**
   * Performs the post-processing of eviction events.
   *
   * @param onlyIfWrites attempts the drain the eviction queues only if there
   *     are pending writes
   */
  void processEvents(boolean onlyIfWrites) {
    tryToDrainEvictionQueues(onlyIfWrites);
    notifyListener();
  }

  /**
   * Notifies the listener of entries that were evicted.
   */
  void notifyListener() {
    Node node;
    while ((node = listenerQueue.poll()) != null) {
      listener.onEviction(node.key, node.weightedValue.value);
    }
  }

  /**
   * A reference to a list node with its recency order.
   */
  final class RecencyReference extends WeakReference<Node> {
    final int recencyOrder;
    RecencyReference next;

    public RecencyReference(Node node, int recencyOrder) {
      super(node);
      this.recencyOrder = recencyOrder;
    }
  }

  /**
   * Adds a node to the page replacement policy and evicts an entry on overflow.
   */
  final class AddTask implements Runnable {
    final Node node;
    final int weight;

    AddTask(Node node, int weight) {
      this.weight = weight;
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      policy.currentSize += weight;
      node.add();
      evict();
    }
  }

  /**
   * Removes a node from the page replacement policy.
   */
  final class RemovalTask implements Runnable {
    final Node node;

    RemovalTask(Node node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      if (node.isLinked()) {
        policy.remove(node);
      }
    }
  }

  /**
   * Updates the weighted size and evicts an entry on overflow.
   */
  final class UpdateTask implements Runnable {
    final int weightDifference;

    public UpdateTask(int weightDifference) {
      this.weightDifference = weightDifference;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      policy.currentSize += weightDifference;
      evict();
    }
  }

  /* ---------------- Concurrent Map Support -------------- */

  @Override
  public boolean isEmpty() {
    return data.isEmpty();
  }

  @Override
  public int size() {
    return data.size();
  }

  /**
   * Returns the weighted size of this map.
   *
   * @return the combined weight of the values in this map
   */
  public int weightedSize() {
    return policy.currentSize;
  }

  @Override
  public void clear() {
    // The alternative is to iterate through the keys and call #remove(), which
    // adds unnecessary contention on the eviction lock and the write queue.
    // Instead the table is walked to conditionally remove the nodes and the
    // linkage fields are null'ed out to reduce GC pressure.
    evictionLock.lock();
    try {
      drainWriteQueue();

      for (Node node : data.values()) {
        if (node.isLinked()) {
          data.remove(node.key, node);
          policy.evict(node);
        }
      }

      // Eagerly discards all of the stale recency reorderings
      for (int i = 0; i < recencyQueue.length; i++) {
        Queue<?> queue = recencyQueue[i];
        int removed = 0;
        while (queue.poll() != null) {
          removed++;
        }
        recencyQueueLength.addAndGet(i, -removed);
      }
    } finally {
      evictionLock.unlock();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    checkNotNull(key, "null key");
    processEvents(true);

    return data.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    checkNotNull(value, "null value");
    processEvents(true);

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
    // blocking other readers. If the entry was found then a recency reorder
    // operation is scheduled on the queue to be applied sometime in the
    // future. The draining of the queues should be delayed until either the
    // recency threshold has been exceeded or if there is a pending write.
    V value = null;
    boolean delayReorder = true;
    Node node = data.get(key);
    if (node != null) {
      delayReorder = addToRecencyQueue(node);
      value = node.weightedValue.value;
    }
    processEvents(delayReorder);
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
  V put(K key, V value, boolean onlyIfAbsent) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    // Per-segment write ordering is required to ensure that the map and write
    // queue are consistently ordered. This is required because if a removal
    // occurs immediately after the put, the concurrent insertion into the queue
    // might allow the removal to be processed first which would corrupt the
    // capacity constraint. The locking is kept slim and if the insertion fails
    // then the operation is treated as a read so that a recency reordering
    // operation is scheduled.
    Node prior;
    V oldValue = null;
    int weightedDifference = 0;
    boolean delayReorder = true;
    int segment = segmentFor(key);
    Lock lock = segmentLock[segment];
    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = new WeightedValue<V>(value, weight);
    Node node = policy.createNode(key, weightedValue, segment);

    // maintain per-segment write ordering
    lock.lock();
    try {
      prior = data.putIfAbsent(node.key, node);
      if (prior == null) {
        writeQueue.add(new AddTask(node, weight));
      } else if (onlyIfAbsent) {
        oldValue = prior.weightedValue.value;
      } else {
        WeightedValue<V> oldWeightedValue = prior.weightedValue;
        weightedDifference = weight - oldWeightedValue.weight;
        prior.weightedValue = weightedValue;
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
      delayReorder = addToRecencyQueue(prior);
    }
    processEvents(delayReorder);
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
    Node node;
    V value = null;
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
    processEvents(true);
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
    processEvents(true);
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
    Node node;
    V prior = null;
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
      delayReorder = addToRecencyQueue(node);
    }
    processEvents(delayReorder);
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
        int weightedDifference = weight - oldWeightedValue.weight;
        writeQueue.add(new UpdateTask(weightedDifference));
      }
      delayReorder = addToRecencyQueue(node);
    }
    processEvents(delayReorder);
    return (oldWeightedValue != null);
  }

  @Override
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return (ks != null) ? ks : (keySet = new KeySet());
  }

  @Override
  public Collection<V> values() {
    Collection<V> vs = values;
    return (vs != null) ? vs : (values = new Values());
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    return (es != null) ? es : (entrySet = new EntrySet());
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

  abstract class Policy {
    @GuardedBy("evictionLock") // must write under lock
    volatile int currentSize;
    @GuardedBy("evictionLock") // must write under lock
    volatile int capacity;

    /** Creates a new sentinel node. */
    abstract Node createSentinel();

    /** Creates a new, unlinked data node. */
    abstract Node createNode(K key, WeightedValue<V> weightedValue, int segment);

    /** Determines the next victim entry to evict from the map. */
    @GuardedBy("evictionLock")
    abstract Node victim();

    @GuardedBy("evictionLock")
    abstract void setCapacity(int capacity);

    final int capacity() {
      return capacity;
    }

    /**
     * Determines whether the map has exceeded its capacity.
     *
     * @return if the map has overflowed and an entry should be evicted
     */
    @GuardedBy("evictionLock")
    boolean hasOverflowed() {
      return currentSize > capacity;
    }

    /**
     * Decrements the weighted size by the node's weight. This method should be
     * called after the node has been removed from the data map, but it may be
     * still referenced by concurrent operations.
     *
     * @param node the entry that was removed
     */
    @GuardedBy("evictionLock")
    abstract void evict(Node node);

    @GuardedBy("evictionLock")
    abstract void remove(Node node);
  }

  final class LruPolicy extends Policy {

    LruPolicy() {
      if (weigher == Weighers.singleton()) {
        throw new AssertionError("LRU should only be used with weighted values");
      }
    }

    @Override
    LruNode createSentinel() {
      return new LruNode();
    }

    @Override
    LruNode createNode(K key, WeightedValue<V> weightedValue, int segment) {
      return new LruNode(key, weightedValue, segment);
    }

    @Override
    @GuardedBy("evictionLock")
    LruNode victim() {
      return ((LruNode) sentinel).next;
    }

    @Override
    @GuardedBy("evictionLock")
    void setCapacity(int capacity) {
      this.capacity = Math.min(capacity, MAXIMUM_CAPACITY);
    }

    @Override
    @GuardedBy("evictionLock")
    void evict(Node node) {
      // Decrements under the segment lock to ensure that a concurrent update
      // to the node's weight has completed.
      Lock lock = segmentLock[node.segment];
      lock.lock();
      try {
        currentSize -= node.weightedValue.weight;
      } finally {
        lock.unlock();
      }
      node.remove();
    }

    @Override
    @GuardedBy("evictionLock")
    void remove(Node node) {
      currentSize -= node.weightedValue.weight;
      node.remove();
    }
  }

  final class LirsPolicy extends Policy {
    @GuardedBy("evictionLock")
    int hotSize;
    @GuardedBy("evictionLock")
    int maximumHotSize;

    LirsPolicy() {
      if (weigher != Weighers.singleton()) {
        throw new AssertionError("LIRS is incompatible with weighted values");
      }
    }

    @Override
    LirsNode createSentinel() {
      return new LirsNode();
    }

    @Override
    LirsNode createNode(K key, WeightedValue<V> weightedValue, int segment) {
      return new LirsNode(key, weightedValue, segment);
    }

    @Override
    @GuardedBy("evictionLock")
    LirsNode victim() {
      // See section 3.3 case #3:
      // "We remove the HIR resident block at the front of list Q (it then
      // becomes a non-resident block), and replace it out of the cache."
      return ((LirsNode) sentinel).nextInQueue;
    }

    /**
     * Sets the maximum number of entries that can be considered hot by the LIRS
     * page replacement algorithm based on the maximum weighted size.
     */
    @Override
    @GuardedBy("evictionLock")
    void setCapacity(int capacity) {
      int maxSize = Math.min(capacity, MAXIMUM_CAPACITY);
      this.capacity = maxSize;

      // If the maximum hot size is equal to the maximum size then the page
      // replacement algorithm degrades to an LRU policy. To avoid this, the
      // maximum hot size is decreased by one when it would otherwise be equal.
      int size = (int) (HOT_RATE * maxSize);
      maximumHotSize = (size == maxSize) ? (maxSize - 1) : size;
    }

    @Override
    @GuardedBy("evictionLock")
    void evict(Node node) {
      remove(node);
    }

    @Override
    @GuardedBy("evictionLock")
    void remove(Node node) {
      currentSize--;
      node.remove();
    }

    @GuardedBy("evictionLock")
    void incrementHotSize() {
      hotSize++;
    }

    @GuardedBy("evictionLock")
    void decrementHotSize() {
      hotSize--;
    }

    boolean canMakeHot() {
      return hotSize < maximumHotSize;
    }
  }

  /**
   * A node contains the key, the weighted value, the segment index, and
   * linkage pointers on the page-replacement algorithm's data structures.
   */
  abstract class Node {
    protected final K key;

    @GuardedBy("segmentLock") // must write under lock
    protected volatile WeightedValue<V> weightedValue;

    /** The segment that the node is associated with. */
    protected final int segment;

    /** Creates a new sentinel node. */
    Node() {
      this.key = null;
      this.segment = -1;
    }

    /** Creates a new, unlinked data node. */
    Node(K key, WeightedValue<V> weightedValue, int segment) {
      this.weightedValue = weightedValue;
      this.segment = segment;
      this.key = key;
    }

    /** Whether the node is linked in the page replacement algorithm. */
    abstract boolean isLinked();

    /** Adds the node to the page replacement algorithm. */
    @GuardedBy("evictionLock")
    abstract void add();

    /** Removes the node from the page replacement algorithm. */
    @GuardedBy("evictionLock")
    abstract void remove();

    /** Applies the recency reordering to the page replacement policy. */
    @GuardedBy("evictionLock")
    abstract void reorder();
  }

  final class LruNode extends Node {
    /**
     * A link to the entry that was less recently used or the sentinel if this
     * entry is the least recent.
     */
    @GuardedBy("evictionLock")
    LruNode prev;

    /**
     * A link to the entry that was more recently used or the sentinel if this
     * entry is the most recent.
     */
    @GuardedBy("evictionLock")
    LruNode next;


    /** Creates a new sentinel node. */
    LruNode() {
      this.prev = next = this;
    }

    /** Creates a new, unlinked data node. */
    LruNode(K key, WeightedValue<V> weightedValue, int segment) {
      super(key, weightedValue, segment);
    }

    @Override
    @GuardedBy("evictionLock")
    void remove() {
      prev.next = next;
      next.prev = prev;
      // null to reduce GC pressure
      prev = next = null;
    }

    /** Appends the node to the tail of the list. */
    @Override
    @GuardedBy("evictionLock")
    void add() {
      LruNode head = (LruNode) sentinel;
      prev = head.prev;
      next = head;
      head.prev.next = this;
      head.prev = this;
    }

    /** Moves the node to the tail of the list. */
    @Override
    @GuardedBy("evictionLock")
    void reorder() {
      if (next != sentinel) {
        prev.next = next;
        next.prev = prev;
        add();
      }
    }

    /** Whether the node is linked on the list. */
    @Override
    @GuardedBy("evictionLock")
    boolean isLinked() {
      return (next != null);
    }
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

  final class LirsNode extends Node {
    /** The LIRS status of the entry. */
    @GuardedBy("evictionLock")
    volatile Status status;

    /**
     * The links on the LIRS stack, S, which maintains recency information. The
     * stack holds all hot entries as well as the cold and non-resident entries
     * that are more recent than the least recent hot entry. The stack is pruned
     * so that the last entry is hot and all entries accessed more recently than
     * the last hot entry are present in the stack, The stack is ordered by
     * recency, with its most recently accessed entry at the top and its least
     * recently accessed entry at the bottom.
     */
    @GuardedBy("evictionLock")
    LirsNode prevInStack;
    @GuardedBy("evictionLock")
    LirsNode nextInStack;

    /**
     * The links on the LIRS queue, Q, which holds all cold entries and supplies
     * the victim entry at the head of the queue for eviction.
     */
    @GuardedBy("evictionLock")
    LirsNode prevInQueue;
    @GuardedBy("evictionLock")
    LirsNode nextInQueue;

    /** Creates a new sentinel node. */
    LirsNode() {
      this.prevInStack = nextInStack = this;
      this.prevInQueue = nextInQueue = this;
    }

    /** Creates a new, unlinked data node. */
    LirsNode(K key, WeightedValue<V> weightedValue, int segment) {
      super(key, weightedValue, segment);
      this.status = Status.NON_RESIDENT;
    }

    /** Whether the node is linked on the stack or queue. */
    @Override
    @GuardedBy("evictionLock")
    boolean isLinked() {
      return inStack() || inQueue();
    }

    @Override
    @GuardedBy("evictionLock")
    void reorder() {
      switch (status) {
        case HOT:
          hotHit();
          break;
        case COLD:
          coldHit();
          break;
        case NON_RESIDENT:
          add();
          break;
        default:
          throw new AssertionError();
      }
    }

    /* ---------------- LIRS Stack Support -------------- */

    /** Adds or moves the node to the top of the stack. */
    @GuardedBy("evictionLock")
    void moveToStack_top() {
      if (inStack()) {
        prevInStack.nextInStack = nextInStack;
        nextInStack.prevInStack = prevInStack;
      }
      addToStackBefore(((LirsNode) sentinel).nextInStack);
    }

    /** Adds or moves the node to the bottom of the stack. */
    @GuardedBy("evictionLock")
    void moveToStack_bottom() {
      if (inStack()) {
        prevInStack.nextInStack = nextInStack;
        nextInStack.prevInStack = prevInStack;
      }
      addToStackBefore((LirsNode) sentinel);
    }

    /** Adds this node before the specified node in the stack. */
    @GuardedBy("evictionLock")
    void addToStackBefore(LirsNode node) {
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

    /** Whether the node is in the stack. */
    @GuardedBy("evictionLock")
    boolean inStack() {
      return (nextInStack != null);
    }

    /* ---------------- LIRS Queue Support -------------- */

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
      LirsNode head = (LirsNode) sentinel;
      prevInQueue = head.prevInQueue;
      nextInQueue = head;
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

    /** Whether the node is in the queue. */
    @GuardedBy("evictionLock")
    boolean inQueue() {
      return (nextInQueue != null);
    }

    /* ---------------- LIRS Eviction Support -------------- */

    /**
     * Moves this entry from the queue to the stack, marking it hot (as cold
     * resident entries must remain in the queue).
     */
    @GuardedBy("evictionLock")
    void migrateToStack() {
      removeFromQueue();
      if (!inStack()) {
        moveToStack_bottom();
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
    void migrateToQueue() {
      removeFromStack();
      makeCold();
    }

    /** Removes the node from the stack and queue. */
    @Override
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
    void makeHot() {
      incrementHotWeightedSize();
      status = Status.HOT;
    }

    /** Becomes a cold entry. */
    @GuardedBy("evictionLock")
    void makeCold() {
      if (status == Status.HOT) {
        subtractFromHotWeightedSize();
      }
      status = Status.COLD;
      moveToQueue_tail();
    }

    @GuardedBy("evictionLock")
    void incrementHotWeightedSize() {
      ((LirsPolicy) policy).incrementHotSize();
    }

    @GuardedBy("evictionLock")
    void subtractFromHotWeightedSize() {
      ((LirsPolicy) policy).decrementHotSize();
    }

    // FIXME(bmanes): Not used?
    /** Becomes a non-resident entry. */
    @GuardedBy("evictionLock")
    void makeNonResident() {
      switch (status) {
        case HOT:
          policy.currentSize++;
          incrementHotWeightedSize();
          status = Status.NON_RESIDENT;
          break;
        case COLD:
          policy.currentSize--;
          status = Status.NON_RESIDENT;
          break;
      }
    }

    /** Records a hit on a hot entry. */
    @GuardedBy("evictionLock")
    void hotHit() {
      // See section 3.3, case #1
      boolean onBottom = (((LirsNode) sentinel).prevInStack == this);
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
        incrementHotWeightedSize();
      }
      if (inQueue()) {
        removeFromQueue();
      }
      ((LirsNode) sentinel).prevInStack.migrateToQueue();
      pruneStack();
    }

    /**
     * Records a hit on a non-resident block. This is how entries join the LIRS
     * stack and queue.
     */
    @Override
    @GuardedBy("evictionLock")
    void add() {
      if (((LirsPolicy) policy).canMakeHot()) {
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
        ((LirsNode) sentinel).prevInStack.migrateToQueue();
        pruneStack();
      } else {
        // If the node is not in the stack, we leave its status in HIR and
        // place it at the end of queue.
        makeCold();
      }
    }

    /**
     * Prunes HIR blocks in the bottom of the stack until an HOT block sits in
     * the stack bottom. If pruned blocks were resident then they remain in the
     * queue; otherwise they are no longer referenced and are evicted.
     */
    @GuardedBy("evictionLock")
    void pruneStack() {
      // See section 3.3:
      // "We define an operation called "stack pruning" on the LIRS stack S,
      // which removes the HIR blocks in the bottom of the stack until an LIR
      // block sits in the stack bottom. This operation serves for two purposes:
      // (1) We ensure the block in the bottom of the stack always belongs to the
      // LIR block set. (2) After the LIR block in the bottom is removed, those
      // HIR blocks contiguously located above it will not have chances to change
      // their status from HIR to LIR, because their recencies are larger than
      // the new maximum recency of LIR blocks."
      LirsNode head = (LirsNode) sentinel;
      LirsNode bottom = head.prevInStack;
      while ((bottom != sentinel) && (bottom.status != Status.HOT)) {
        if (bottom.status == Status.NON_RESIDENT) {
          // Notify the listener if the entry was evicted
          if (data.remove(bottom.key, bottom)) {
            listenerQueue.add(bottom);
          }
          policy.currentSize--;
          bottom.remove();
        } else {
          bottom.removeFromStack();
        }
        bottom = head.prevInStack;
      }
    }
  }

  /**
   * An adapter to safely externalize the keys.
   */
  final class KeySet extends AbstractSet<K> {
    final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

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
  final class KeyIterator implements Iterator<K> {
    final EntryIterator iterator = new EntryIterator(data.values().iterator());

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
  final class Values extends AbstractCollection<V> {

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
  final class ValueIterator implements Iterator<V> {
    final EntryIterator iterator = new EntryIterator(data.values().iterator());

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
  final class EntrySet extends AbstractSet<Entry<K, V>> {
    final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

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
      if (!(obj instanceof Entry<?, ?>)) {
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
      if (!(obj instanceof Entry<?, ?>)) {
        return false;
      }
      Entry<?, ?> entry = (Entry<?, ?>) obj;
      return map.remove(entry.getKey(), entry.getValue());
    }
  }

  /**
   * An adapter to safely externalize the entry iterator.
   */
  final class EntryIterator implements Iterator<Entry<K, V>> {
    final Iterator<Node> iterator;
    Node current;

    EntryIterator(Iterator<Node> iterator) {
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
  final class WriteThroughEntry extends SimpleEntry<K, V> {
    static final long serialVersionUID = 1;

    WriteThroughEntry(Node node) {
      super(node.key, node.weightedValue.value);
    }

    @Override
    public V setValue(V value) {
      put(getKey(), value);
      return super.setValue(value);
    }

    Object writeReplace() {
      return new SimpleEntry<K, V>(this);
    }
  }

  /**
   * A queue that discards all additions and is always empty.
   */
  static final class DiscardingQueue<E> extends AbstractQueue<E> {
    @Override public boolean add(E e) { return true; }
    @Override public boolean offer(E e) { return true; }
    @Override public E poll() { return null; }
    @Override public E peek() { return null; }
    @Override public int size() { return 0; }
    @Override public Iterator<E> iterator() { return Collections.<E>emptyList().iterator(); }
  }

  /**
   * A listener that ignores all notifications.
   */
  enum DiscardingListener implements EvictionListener<Object, Object> {
    INSTANCE;

    @Override public void onEviction(Object key, Object value) {}
  }

  /* ---------------- Serialization Support -------------- */

  static final long serialVersionUID = 1;

  Object writeReplace() {
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
  static final class SerializationProxy<K, V> implements Serializable {
    final EvictionListener<K, V> listener;
    final Weigher<? super V> weigher;
    final int concurrencyLevel;
    final Map<K, V> data;
    final int capacity;

    SerializationProxy(ConcurrentLinkedHashMap<K, V> map) {
      concurrencyLevel = map.concurrencyLevel;
      capacity = map.policy.capacity();
      data = new HashMap<K, V>(map);
      listener = map.listener;
      weigher = map.weigher;
    }

    Object readResolve() {
      ConcurrentLinkedHashMap<K, V> map = new Builder<K, V>()
          .concurrencyLevel(concurrencyLevel)
          .maximumWeightedCapacity(capacity)
          .listener(listener)
          .weigher(weigher)
          .build();
      map.putAll(data);
      return map;
    }

    static final long serialVersionUID = 1;
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
  public static final class Builder<K, V> {
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    EvictionListener<K, V> listener;
    Weigher<? super V> weigher;

    int maximumWeightedCapacity;
    int concurrencyLevel;
    int initialCapacity;

    @SuppressWarnings("unchecked")
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
     * @throws NullPointerException if the listener is null
     */
    public Builder<K, V> listener(EvictionListener<K, V> listener) {
      checkNotNull(listener, null);
      this.listener = listener;
      return this;
    }

    /**
     * Specifies an algorithm to determine how many the units of capacity a
     * value consumes. The default algorithm bounds the map by the number of
     * key-value pairs by giving each entry a weight of <tt>1</tt>.
     *
     * @param weigher the algorithm to determine a value's weight
     * @throws NullPointerException if the weigher is null
     */
    public Builder<K, V> weigher(Weigher<? super V> weigher) {
      checkNotNull(weigher, null);
      this.weigher = weigher;
      return this;
    }

    /**
     * Creates a new {@link ConcurrentLinkedHashMap} instance.
     *
     * @throws IllegalStateException if the maximum weighted capacity was
     *     not set
     */
    public ConcurrentLinkedHashMap<K, V> build() {
      if (maximumWeightedCapacity < 0) {
        throw new IllegalStateException();
      }
      return new ConcurrentLinkedHashMap<K, V>(this);
    }
  }
}
