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

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DrainStatus.IDLE;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DrainStatus.PROCESSING;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DrainStatus.REQUIRED;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
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
 * its <tt>maximum weighted capacity</tt> threshold. A {@link Weigher} instance
 * determines how many units of capacity that a value consumes. The default
 * weigher assigns each value a weight of <tt>1</tt> to bound the map by the
 * total number of key-value pairs. A map that holds collections may choose to
 * weigh values by the number of elements in the collection and bound the map
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
 * predictable iteration order. A snapshot of the keys and entries may be
 * obtained in ascending and descending order of retention.
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
  // capacity is exceeded.
  //
  // The page replacement algorithm's data structures are kept eventually
  // consistent with the map. An update to the map and recording of reads may
  // not be immediately reflected on the algorithm's data structures. These
  // structures are guarded by a lock and operations are applied in batches to
  // avoid lock contention. The penalty of applying the batches is spread across
  // threads so that the amortized cost is slightly higher than performing just
  // the ConcurrentHashMap operation.
  //
  // A memento of the reads and writes that were performed on the map are
  // recorded in the queues. The queues are drained at the first opportunity
  // after a write or when a queue exceeds its capacity threshold. A strict
  // ordering is achieved by observing that each queue is in a weakly sorted
  // order starting from the last drain. The queues can be merged in O(n) time
  // so that the operations are applied in the expected order.
  //
  // The Least Recently Used page replacement algorithm was chosen due to its
  // simplicity, high hit rate, and ability to be implemented with O(1) time
  // complexity.

  /** The maximum weighted capacity of the map. */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /** The maximum weight of a value. */
  static final int MAXIMUM_WEIGHT = 1 << 29;

  /** The maximum number of pending operations per buffer. */
  static final int MAXIMUM_BUFFER_SIZE = 1 << 20;

  /** The number of buffers to use. */
  static final int NUMBER_OF_BUFFERS;

  /** Mask value for indexing into the buffers. */
  static final int BUFFER_MASK;

  /** The maximum number of pending operations to perform per drain. */
  static final int MAXIMUM_OPERATIONS_TO_DRAIN;

  /**
   * Number of cache operations that can be buffered per recency queue
   * before the cache's recency ordering information is updated. This is used
   * to avoid lock contention by recording a memento of reads and delaying a
   * lock acquisition until the threshold is crossed or a mutation occurs.
   */
  static final int BUFFER_THRESHOLD = 16;

  static {
    // Find the power-of-two best matching the number of available processors
    int recencyQueues = 1;
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    while (recencyQueues < availableProcessors) {
      recencyQueues <<= 1;
    }
    MAXIMUM_OPERATIONS_TO_DRAIN = (1 + recencyQueues) * BUFFER_THRESHOLD;
    NUMBER_OF_BUFFERS = recencyQueues;
    BUFFER_MASK = recencyQueues - 1;
  }

  /** An executor that runs the task on the caller's thread. */
  static final Executor sameThreadExecutor = new SameThreadExecutor();

  /** A queue that discards all entries. */
  static final Queue<?> discardingQueue = new DiscardingQueue<Object>();

  enum DrainStatus { IDLE, REQUIRED, PROCESSING }

  final AtomicReference<DrainStatus> drainStatus;

  /** The backing data store holding the key-value associations. */
  final ConcurrentMap<K, Node> data;
  final int concurrencyLevel;

  // These fields provide support to bound the map by a maximum capacity.
  @GuardedBy("evictionLock")
  final LinkedDeque<Node> evictionDeque;

  @GuardedBy("evictionLock") // must write under lock
  volatile int weightedSize;
  @GuardedBy("evictionLock") // must write under lock
  volatile int capacity;

  volatile int globalRecencyOrder;
  @GuardedBy("evictionLock")
  int drainedRecencyOrder;

  final Lock evictionLock;
  final Executor executor;
  final Weigher<? super V> weigher;
  final AtomicIntegerArray bufferLength;
  final Queue<OrderedTask>[] buffers;

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
    // The data store and its maximum capacity
    concurrencyLevel = builder.concurrencyLevel;
    capacity = Math.min(builder.capacity, MAXIMUM_CAPACITY);
    data = new ConcurrentHashMap<K, Node>(builder.initialCapacity, 0.75f, concurrencyLevel);

    // The eviction support
    weigher = builder.weigher;
    executor = builder.executor;
    evictionLock = new ReentrantLock();
    globalRecencyOrder = Integer.MIN_VALUE;
    drainedRecencyOrder = Integer.MIN_VALUE;
    evictionDeque = new LinkedDeque<Node>();
    drainStatus = new AtomicReference<DrainStatus>(IDLE);

    buffers = (Queue<OrderedTask>[]) new Queue[NUMBER_OF_BUFFERS];
    bufferLength = new AtomicIntegerArray(NUMBER_OF_BUFFERS);
    for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
      buffers[i] = new ConcurrentLinkedQueue<OrderedTask>();
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
    return capacity;
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
    this.capacity = Math.min(capacity, MAXIMUM_CAPACITY);

    evictionLock.lock();
    try {
      drainRecencyQueues();
      evict();
    } finally {
      evictionLock.unlock();
    }
    notifyListener();
  }

  /**
   * Determines whether the map has exceeded its capacity.
   */
  boolean hasOverflowed() {
    return weightedSize > capacity;
  }

  /**
   * Evicts entries from the map while it exceeds the capacity and appends
   * evicted entries to the listener queue for processing.
   */
  @GuardedBy("evictionLock")
  void evict() {
    // Attempts to evict entries from the map if it exceeds the maximum
    // capacity. If the eviction fails due to a concurrent removal of the
    // victim, that removal may cancel out the addition that triggered this
    // eviction. The victim is eagerly unlinked before the removal task so
    // that if an eviction is still required then a new victim will be chosen
    // for removal.
    while (hasOverflowed()) {
      if (evictionDeque.isEmpty()) {
        // If weighted, pending operations will adjust the size to reflect
        // the correct weight.
        return;
      }

      Node node = evictionDeque.poll();
      boolean evicted = data.remove(node.key, node);
      WeightedValue<V> weightedValue;
      for (;;) {
        weightedValue = node.getWeightedValue();
        if (weightedValue.isTombstone()) {
          break;
        } else {
          WeightedValue<V> tombstone = WeightedValue.createPendingRemoval(weightedValue);
          if (node.casWeightedValue(weightedValue, tombstone)) {
            break;
          }
        }
      }

      decrementWeightFor(node);
      if (evicted) {
        // Notify the listener if the entry was evicted
        listenerQueue.add(node);
      } else {
        // Allow the pending removal task to no-op
        node.set(WeightedValue.createRemoved(weightedValue));
      }
    }
  }

  /**
   * Decrements the weighted size by the node's weight. This method should be
   * called after the node has been removed from the data map, but it may be
   * still referenced by concurrent operations.
   *
   * @param node the entry that was removed
   */
  @GuardedBy("evictionLock")
  void decrementWeightFor(Node node) {
    if (weigher == Weighers.singleton()) {
      weightedSize--;
    } else {
      weightedSize -= Math.abs(node.getWeightedValue().weight);
    }
  }

  /**
   * Adds the entry to the recency queue for a future update to the page
   * replacement algorithm. An entry should be added to the queue when it is
   * accessed on either a read or update operation. This aids the page
   * replacement algorithm in choosing the best victim when an eviction is
   * required.
   *
   * @param task the pending operation to apply
   */
  void addToRecencyQueue(Runnable task, boolean isWrite) {
    // The recency's global order is acquired in a racy fashion as the increment
    // is not atomic with the insertion. This means that concurrent reads can
    // have the same ordering and the queues are in a weakly sorted order.
    int recencyOrder = globalRecencyOrder++;

    int index = bufferIndex();
    int buffered = bufferLength.incrementAndGet(index);

    // A buffer may discard a read task if its length exceeds a tolerance level
    if ((buffered <= MAXIMUM_BUFFER_SIZE) || isWrite) {
      buffers[index].add(new OrderedTask(recencyOrder, task, isWrite));
    } else {
      bufferLength.decrementAndGet(index);
    }

    boolean delayReorder;
    if (isWrite) {
      delayReorder = false;
      drainStatus.set(REQUIRED);
    } else {
      delayReorder = (buffered <= BUFFER_THRESHOLD);
    }
    processEvents(delayReorder);
  }

  /** Returns the index to the buffer that the task should be scheduled on. */
  static int bufferIndex() {
    // A buffer is chosen by the thread's id so that read tasks are evenly
    // distributed. This ensures that hot entries do not suffer contention due
    // to the threads trying to append to the same buffer.
    return (int) Thread.currentThread().getId() & BUFFER_MASK;
  }

  /**
   * Attempts to acquire the eviction lock and apply pending updates to the
   * eviction algorithm.
   *
   * @param onlyIfWrites attempts to drain the eviction queues only if there
   *     are pending writes
   */
  void tryToDrainEvictionQueues(boolean onlyIfWrites) {
    if (onlyIfWrites && (drainStatus.get() != REQUIRED)) {
      return;
    }
    if (evictionLock.tryLock()) {
      try {
        drainStatus.set(PROCESSING);
        drainRecencyQueues();
        drainStatus.compareAndSet(PROCESSING, IDLE);
      } finally {
        evictionLock.unlock();
      }
    }
  }

  /**
   * Drains the recency queues and applies the pending reorderings.
   */
  @GuardedBy("evictionLock")
  void drainRecencyQueues() {
    // A strict recency ordering is achieved by observing that each queue
    // contains recencies in weakly sorted order starting from the last drain.
    // The queues can be merged into a single weakly sorted list in O(n) time
    // by using counting sort and chaining on a collision.

    // The merged output is capped to the expected number of recencies plus
    // additional slack to optimistically handle the concurrent additions to
    // the queues.
    OrderedTask[] recencies = new OrderedTask[MAXIMUM_OPERATIONS_TO_DRAIN];

    // Moves the recencies into the output collections, applies the reorderings,
    // and updates the marker for the starting recency order of the next drain.
    int maxRecencyIndex = moveRecenciesFromQueues(recencies);
    applyRecencyTasks(recencies, maxRecencyIndex);
    updateDrainedRecencyOrder(recencies, maxRecencyIndex);
  }

  /**
   * Moves the recencies from the recency queues into the output array.
   *
   * @param recencies the ordered array of the pending recency operations
   * @return the highest index location of a recency that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveRecenciesFromQueues(OrderedTask[] recencies) {
    int maxRecencyIndex = -1;
    for (int i = 0; i < buffers.length; i++) {
      int maxIndex = moveRecenciesFromQueue(i, recencies);
      maxRecencyIndex = Math.max(maxIndex, maxRecencyIndex);
    }
    return maxRecencyIndex;
  }

  /**
   * Moves the recencies from the specified queue into the output array.
   *
   * @param queueIndex the recency queue to drain
   * @param recencies the ordered array of the pending recency operations
   * @return the highest index location of a recency that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveRecenciesFromQueue(int queueIndex, OrderedTask[] recencies) {
    // While a queue is being drained it may be concurrently appended to. The
    // number of elements removed are tracked so that the length can be
    // decremented by the delta rather than set to zero.
    Queue<OrderedTask> queue = buffers[queueIndex];
    int removedFromQueue = 0;

    int maxIndex = -1;
    OrderedTask recency;
    while ((recency = queue.poll()) != null) {
      removedFromQueue++;

      // The index into the output array is determined by calculating the offset
      // since the last drain.
      int index = recency.recencyOrder - drainedRecencyOrder;
      if (index < 0) {
        // The recency was missed by the last drain (due to the queues being
        // weakly sorted) and can be applied immediately.
        applyRecency(recency);
        continue;
      } else if (index >= recencies.length) {
        // Due to concurrent additions, the recency order exceeds the capacity
        // of the output array. It is added to the end as overflow and the
        // remaining recencies in the queue will be handled by the next drain.
        maxIndex = recencies.length - 1;
        addRecencyToChain(recencies, recency, maxIndex);
        break;
      } else {
        maxIndex = Math.max(index, maxIndex);
        addRecencyToChain(recencies, recency, index);
      }
    }
    bufferLength.addAndGet(queueIndex, -removedFromQueue);
    return maxIndex;
  }

  /**
   * Adds the recency as the head of the chain at the index location.
   *
   * @param recencies the ordered array of the pending recency operations
   * @param recency the pending recency operation
   * @param index the array location
   */
  @GuardedBy("evictionLock")
  void addRecencyToChain(OrderedTask[] recencies, OrderedTask recency, int index) {
    recency.next = recencies[index];
    recencies[index] = recency;
  }

  /**
   * Applies the pending recency tasks to the page replacement policy.
   *
   * @param recencies the ordered array of the pending recency operations
   * @param maxRecencyIndex the maximum index of the recencies array
   */
  @GuardedBy("evictionLock")
  void applyRecencyTasks(OrderedTask[] recencies, int maxRecencyIndex) {
    for (int i = 0; i <= maxRecencyIndex; i++) {
      applyRecencyiesInChain(recencies[i]);
    }
  }

  /**
   * Applies the pending recency operations for the recencies on the linked
   * chain.
   *
   * @param head the first recency in the chain of recency operations
   */
  @GuardedBy("evictionLock")
  void applyRecencyiesInChain(OrderedTask head) {
    OrderedTask recency = head;
    while (recency != null) {
      applyRecency(recency);
      recency = recency.next;
    }
  }

  /**
   * Applies the pending recency reorderings to the page replacement policy.
   *
   * @param recency the pending recency operation
   */
  @GuardedBy("evictionLock")
  void applyRecency(OrderedTask recency) {
    recency.run();
  }

  /**
   * Updates the recency order to start the next drain from.
   *
   * @param recencies the ordered array of the recency operations
   * @param maxRecencyIndex the maximum index of the recencies array
   */
  @GuardedBy("evictionLock")
  void updateDrainedRecencyOrder(OrderedTask[] recencies, int maxRecencyIndex) {
    if (maxRecencyIndex >= 0) {
      OrderedTask recency = recencies[maxRecencyIndex];
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
      listener.onEviction(node.key, node.getWeightedValue().value);
    }
  }

  static final class OrderedTask implements Runnable {
    final int recencyOrder;
    final boolean isWrite;
    final Runnable task;
    OrderedTask next;

    OrderedTask(int recencyOrder, Runnable task, boolean isWrite) {
      this.recencyOrder = recencyOrder;
      this.isWrite = isWrite;
      this.task = task;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      task.run();
    }

    boolean isWrite() {
      return isWrite;
    }
  }

  /**
   * A reference to a list node with its recency order.
   */
  class RecencyReference extends WeakReference<Node> implements Runnable {

    public RecencyReference(Node node) {
      super(node);
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      // An entry may scheduled for reordering despite having been previously
      // removed. This can occur when the entry was concurrently read while a
      // writer was removing it. If the entry was garbage collected or no longer
      // linked then it does not need to be processed.
      Node node = get();
      if ((node != null) && evictionDeque.contains(node)) {
        evictionDeque.moveToBack(node);
      }
    }
  }

  /**
   * Adds a node to the list and evicts an entry on overflow.
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
      weightedSize += weight;
      if (node.getWeightedValue().isTombstone()) {
        return; // ignore out-of-order write operations
      }
      evictionDeque.add(node);
      evict();
    }
  }

  /**
   * Removes a node from the list.
   */
  final class RemovalTask implements Runnable {
    final Node node;

    RemovalTask(Node node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      WeightedValue<V> weightedValue = node.getWeightedValue();
      weightedSize -= Math.abs(weightedValue.weight);
      evictionDeque.remove(node);
    }
  }

  /**
   * Updates the weighted size and evicts an entry on overflow.
   */
  final class UpdateTask extends RecencyReference {
    final int weightDifference;

    public UpdateTask(Node node, int weightDifference) {
      super(node);
      this.weightDifference = weightDifference;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      super.run();
      weightedSize += weightDifference;
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
    return Math.max(weightedSize, 0);
  }

  @Override
  public void clear() {
    // The alternative is to iterate through the keys and call #remove(), which
    // adds unnecessary contention on the eviction lock and the write queue.
    // Instead the table is walked to conditionally remove the nodes and the
    // linkage fields are null'ed out to reduce GC pressure.
    evictionLock.lock();
    try {
      Node node;
      while ((node = evictionDeque.poll()) != null) {
        data.remove(node.key, node);
        decrementWeightFor(node);
        node.set(WeightedValue.createRemoved(node.getWeightedValue()));
      }

      // Drain the queues to apply the writes and discard the reorderings
      for (int i = 0; i < buffers.length; i++) {
        Queue<OrderedTask> queue = buffers[i];
        OrderedTask task;
        int removed = 0;
        while ((task = queue.poll()) != null) {
          removed++;
          if (task.isWrite()) {
            task.run();
          }
        }
        bufferLength.addAndGet(i, -removed);
      }
    } finally {
      evictionLock.unlock();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    checkNotNull(key, "null key");
    return data.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    checkNotNull(value, "null value");

    for (Node node : data.values()) {
      if (node.getWeightedValue().value.equals(value)) {
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
    Node node = data.get(key);
    if (node == null) {
      return null;
    }
    addToRecencyQueue(new RecencyReference(node), false);
    return node.getWeightedValue().value;
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

    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = WeightedValue.create(value, weight);
    Node node = new Node(key, weightedValue);

    for (;;) {
      Node prior = data.putIfAbsent(node.key, node);
      if (prior == null) {
        addToRecencyQueue(new AddTask(node, weight), true);
        return null;
      } else if (onlyIfAbsent) {
        addToRecencyQueue(new RecencyReference(prior), false);
        return prior.getWeightedValue().value;
      }
      for (;;) {
        WeightedValue<V> oldWeightedValue = prior.getWeightedValue();
        if (oldWeightedValue.isTombstone()) {
          break;
        }

        if (prior.casWeightedValue(oldWeightedValue, weightedValue)) {
          int weightedDifference = weight - oldWeightedValue.weight;
          V oldValue = oldWeightedValue.value;
          if (weightedDifference == 0) {
            addToRecencyQueue(new RecencyReference(prior), false);
          } else {
            addToRecencyQueue(new UpdateTask(prior, weightedDifference), true);
          }
          return oldValue;
        }
      }
    }
  }

  @Override
  public V remove(Object key) {
    checkNotNull(key, "null key");

    Node node = data.remove(key);
    if (node == null) {
      return null;
    }
    for (;;) {
      WeightedValue<V> weightedValue = node.getWeightedValue();
      WeightedValue<V> tombstone = WeightedValue.createPendingRemoval(weightedValue);
      if (node.casWeightedValue(weightedValue, tombstone)) {
        addToRecencyQueue(new RemovalTask(node), true);
        return weightedValue.value;
      }
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    Node node = data.get(key);
    if (node == null) {
      return false;
    }
    WeightedValue<V> weightedValue = node.getWeightedValue();
    if (node.getWeightedValue().value.equals(value)) {
      WeightedValue<V> tombstone = WeightedValue.createPendingRemoval(weightedValue);
      if (node.casWeightedValue(weightedValue, tombstone)) {
        data.remove(key, node);
        addToRecencyQueue(new RemovalTask(node), true);
        return true;
      }
    }
    return false;
  }

  @Override
  public V replace(K key, V value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    int weight = weigher.weightOf(value);
    WeightedValue<V> weightedValue = WeightedValue.create(value, weight);

    for (;;) {
      Node node = data.get(key);
      if (node == null) {
        return null;
      }
      WeightedValue<V> oldWeightedValue = node.getWeightedValue();
      if (oldWeightedValue.isTombstone()) {
        return null;
      }
      if (node.casWeightedValue(oldWeightedValue, weightedValue)) {
        int weightedDifference = weight - oldWeightedValue.weight;
        if (weightedDifference == 0) {
          addToRecencyQueue(new RecencyReference(node), false);
        } else {
          addToRecencyQueue(new UpdateTask(node, weightedDifference), true);
        }
        return oldWeightedValue.value;
      }
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkNotNull(key, "null key");
    checkNotNull(oldValue, "null oldValue");
    checkNotNull(newValue, "null newValue");

    int weight = weigher.weightOf(newValue);
    WeightedValue<V> newWeightedValue = WeightedValue.create(newValue, weight);

    Node node = data.get(key);
    if (node == null) {
      return false;
    }
    for (;;) {
      WeightedValue<V> weightedValue = node.getWeightedValue();
      if (weightedValue.isTombstone() || !oldValue.equals(weightedValue.value)) {
        return false;
      }
      if (node.casWeightedValue(weightedValue, newWeightedValue)) {
        int weightedDifference = weight - weightedValue.weight;
        addToRecencyQueue(new UpdateTask(node, weightedDifference), true);
        return true;
      }
    }
  }

  @Override
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return (ks != null) ? ks : (keySet = new KeySet());
  }

  /**
   * Returns a unmodifiable snapshot {@link Set} view of the keys contained in
   * this map. The set's iterator returns the keys whose order of iteration is
   * the ascending order in which its entries are considered eligible for
   * retention, from the least-likely to be retained to the most-likely.
   * <p>
   * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em>
   * a constant-time operation. Because of the asynchronous nature of the page
   * replacement policy, determining the retention ordering requires a traversal
   * of the keys.
   *
   * @return an ascending snapshot view of the keys in this map
   */
  public Set<K> ascendingKeySet() {
    return orderedKeySet(true, Integer.MAX_VALUE);
  }

  /**
   * Returns a unmodifiable snapshot {@link Set} view of the keys contained in
   * this map. The set's iterator returns the keys whose order of iteration is
   * the ascending order in which its entries are considered eligible for
   * retention, from the least-likely to be retained to the most-likely.
   * <p>
   * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em>
   * a constant-time operation. Because of the asynchronous nature of the page
   * replacement policy, determining the retention ordering requires a traversal
   * of the keys.
   *
   * @param limit the maximum size of the returned set
   * @return an ascending snapshot view of the keys in this map
   * @throws IllegalArgumentException if the limit is negative
   */
  public Set<K> ascendingKeySetWithLimit(int limit) {
    return orderedKeySet(true, limit);
  }

  /**
   * Returns a unmodifiable snapshot {@link Set} view of the keys contained in
   * this map. The set's iterator returns the keys whose order of iteration is
   * the descending order in which its entries are considered eligible for
   * retention, from the most-likely to be retained to the least-likely.
   * <p>
   * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em>
   * a constant-time operation. Because of the asynchronous nature of the page
   * replacement policy, determining the retention ordering requires a traversal
   * of the keys.
   *
   * @return an descending snapshot view of the keys in this map
   */
  public Set<K> descendingKeySet() {
    return orderedKeySet(false, Integer.MAX_VALUE);
  }

  /**
   * Returns a unmodifiable snapshot {@link Set} view of the keys contained in
   * this map. The set's iterator returns the keys whose order of iteration is
   * the descending order in which its entries are considered eligible for
   * retention, from the most-likely to be retained to the least-likely.
   * <p>
   * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em>
   * a constant-time operation. Because of the asynchronous nature of the page
   * replacement policy, determining the retention ordering requires a traversal
   * of the keys.
   *
   * @param limit the maximum size of the returned set
   * @return an descending snapshot view of the keys in this map
   * @throws IllegalArgumentException if the limit is negative
   */
  public Set<K> descendingKeySetWithLimit(int limit) {
    return orderedKeySet(false, limit);
  }

  Set<K> orderedKeySet(boolean ascending, int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException();
    }
    evictionLock.lock();
    try {
      drainRecencyQueues();

      int size = Math.min(limit, evictionDeque.size());
      Set<K> keys = new LinkedHashSet<K>(size);
      Iterator<Node> iterator = ascending
          ? evictionDeque.iterator()
          : evictionDeque.descendingIterator();
      while (iterator.hasNext() && (limit > keys.size())) {
        keys.add(iterator.next().key);
      }
      return Collections.unmodifiableSet(keys);
    } finally {
      evictionLock.unlock();
    }
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
   * Returns a unmodifiable snapshot {@link Map} view of the mappings contained
   * in this map. The map's collections return the mappings whose order of
   * iteration is the ascending order in which its entries are considered
   * eligible for retention, from the least-likely to be retained to the
   * most-likely.
   * <p>
   * Beware that obtaining the mappings is <em>NOT</em> a constant-time
   * operation. Because of the asynchronous nature of the page replacement
   * policy, determining the retention ordering requires a traversal of the
   * entries.
   *
   * @return an ascending snapshot view of this map
   */
  public Map<K, V> ascendingMap() {
    return orderedMap(true, Integer.MAX_VALUE);
  }

  /**
   * Returns a unmodifiable snapshot {@link Map} view of the mappings contained
   * in this map. The map's collections return the mappings whose order of
   * iteration is the ascending order in which its entries are considered
   * eligible for retention, from the least-likely to be retained to the
   * most-likely.
   * <p>
   * Beware that obtaining the mappings is <em>NOT</em> a constant-time
   * operation. Because of the asynchronous nature of the page replacement
   * policy, determining the retention ordering requires a traversal of the
   * entries.
   *
   * @param limit the maximum size of the returned map
   * @return an ascending snapshot view of this map
   * @throws IllegalArgumentException if the limit is negative
   */
  public Map<K, V> ascendingMapWithLimit(int limit) {
    return orderedMap(true, limit);
  }

  /**
   * Returns a unmodifiable snapshot {@link Map} view of the mappings contained
   * in this map. The map's collections return the mappings whose order of
   * iteration is the descending order in which its entries are considered
   * eligible for retention, from the most-likely to be retained to the
   * least-likely.
   * <p>
   * Beware that obtaining the mappings is <em>NOT</em> a constant-time
   * operation. Because of the asynchronous nature of the page replacement
   * policy, determining the retention ordering requires a traversal of the
   * entries.
   *
   * @return an descending snapshot view of this map
   */
  public Map<K, V> descendingMap() {
    return orderedMap(false, Integer.MAX_VALUE);
  }

  /**
   * Returns a unmodifiable snapshot {@link Map} view of the mappings contained
   * in this map. The map's collections return the mappings whose order of
   * iteration is the descending order in which its entries are considered
   * eligible for retention, from the most-likely to be retained to the
   * least-likely.
   * <p>
   * Beware that obtaining the mappings is <em>NOT</em> a constant-time
   * operation. Because of the asynchronous nature of the page replacement
   * policy, determining the retention ordering requires a traversal of the
   * entries.
   *
   * @param limit the maximum size of the returned map
   * @return an descending snapshot view of this map
   * @throws IllegalArgumentException if the limit is negative
   */
  public Map<K, V> descendingMapWithLimit(int limit) {
    return orderedMap(false, limit);
  }

  Map<K, V> orderedMap(boolean ascending, int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException();
    }
    evictionLock.lock();
    try {
      drainRecencyQueues();

      int size = Math.min(limit, evictionDeque.size());
      Map<K, V> map = new LinkedHashMap<K, V>(size);
      Iterator<Node> iterator = ascending
          ? evictionDeque.iterator()
          : evictionDeque.descendingIterator();
      while (iterator.hasNext() && (limit > map.size())) {
        Node node = iterator.next();
        map.put(node.key, node.getWeightedValue().value);
      }
      return Collections.unmodifiableMap(map);
    } finally {
      evictionLock.unlock();
    }
  }

  /** A value and its weight. */
  @Immutable
  static final class WeightedValue<V> {
    final int weight;
    final V value;

    WeightedValue(V value, int weight) {
      this.weight = weight;
      this.value = value;
    }

    static <V> WeightedValue<V> create(V value, int weight) {
      if ((weight < 1) || (weight > MAXIMUM_WEIGHT)) {
        throw new IllegalArgumentException("invalid weight");
      }
      return new WeightedValue<V>(value, weight);
    }

    static <V> WeightedValue<V> createPendingRemoval(WeightedValue<V> weightedValue) {
      return new WeightedValue<V>(weightedValue.value, -weightedValue.weight);
    }

    static <V> WeightedValue<V> createRemoved(WeightedValue<V> weightedValue) {
      return new WeightedValue<V>(weightedValue.value, 0);
    }

    boolean isTombstone() {
      return weight <= 0;
    }
  }

  /**
   * A node contains the key, the weighted value, and the linkage pointers on
   * the page-replacement algorithm's data structures.
   */
  @SuppressWarnings("serial")
  final class Node extends AtomicReference<WeightedValue<V>> implements Linked<Node> {
    final K key;
    @GuardedBy("evictionLock")
    Node prev;
    @GuardedBy("evictionLock")
    Node next;

    /** Creates a new, unlinked node. */
    Node(K key, WeightedValue<V> weightedValue) {
      super(weightedValue);
      this.key = key;
      this.prev = null;
      this.next = null;
    }

    @Override
    @GuardedBy("evictionLock")
    public Node getPrevious() {
      return prev;
    }

    @Override
    @GuardedBy("evictionLock")
    public void setPrevious(Node prev) {
      this.prev = prev;
    }

    @Override
    @GuardedBy("evictionLock")
    public Node getNext() {
      return next;
    }

    @Override
    @GuardedBy("evictionLock")
    public void setNext(Node next) {
      this.next = next;
    }

    WeightedValue<V> getWeightedValue() {
      return get();
    }

    boolean casWeightedValue(WeightedValue<V> expect, WeightedValue<V> update) {
      return compareAndSet(expect, update);
    }
  }

  /** An adapter to safely externalize the keys. */
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

  /** An adapter to safely externalize the key iterator. */
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

  /** An adapter to safely externalize the values. */
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

  /** An adapter to safely externalize the value iterator. */
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

  /** An adapter to safely externalize the entries. */
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
      return (node != null) && (node.getWeightedValue().value.equals(entry.getValue()));
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

  /** An adapter to safely externalize the entry iterator. */
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
      ConcurrentLinkedHashMap.this.remove(current.key, current.getWeightedValue().value);
      current = null;
    }
  }

  /** An entry that allows updates to write through to the map. */
  final class WriteThroughEntry extends SimpleEntry<K, V> {
    static final long serialVersionUID = 1;

    WriteThroughEntry(Node node) {
      super(node.key, node.getWeightedValue().value);
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

  /** An executor that runs the task on the caller's thread. */
  static final class SameThreadExecutor implements Executor {
    @Override public void execute(Runnable command) {
      command.run();
    }
  }

  /** A queue that discards all additions and is always empty. */
  static final class DiscardingQueue<E> extends AbstractQueue<E> {
    @Override public boolean add(E e) { return true; }
    @Override public boolean offer(E e) { return true; }
    @Override public E poll() { return null; }
    @Override public E peek() { return null; }
    @Override public int size() { return 0; }
    @Override public Iterator<E> iterator() { return Collections.<E>emptyList().iterator(); }
  }

  /** A listener that ignores all notifications. */
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
      capacity = map.capacity;
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
   * ConcurrentMap<Vertices, Set<Edge>> graph = new Builder<Vertice, Set<Edge>>()
   *     .weigher(Weighers.<Group>set())
   *     .maximumCapacity(5000)
   *     .build();
   * </pre>
   */
  public static final class Builder<K, V> {
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    EvictionListener<K, V> listener;
    Weigher<? super V> weigher;
    Executor executor;

    int capacity;
    int concurrencyLevel;
    int initialCapacity;

    @SuppressWarnings("unchecked")
    public Builder() {
      capacity = -1;
      executor = sameThreadExecutor;
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
     * @param capacity the weighted threshold to bound the map by
     * @throws IllegalArgumentException if the maximumWeightedCapacity is
     *     negative
     */
    public Builder<K, V> maximumWeightedCapacity(int capacity) {
      if (capacity < 0) {
        throw new IllegalArgumentException();
      }
      this.capacity = capacity;
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
     * Specifies an executor for use in catching up the page replacement policy.
     * If unspecified, the catching up will be performed on user threads during
     * write operations (or during read operations, in the absence of writes).
     *
     * @throws NullPointerException if the executor is null
     */
    Builder<K, V> catchup(Executor executor) {
      checkNotNull(executor, null);
      this.executor = executor;
      return this;
    }

    /**
     * Creates a new {@link ConcurrentLinkedHashMap} instance.
     *
     * @throws IllegalStateException if the maximum weighted capacity was
     *     not set
     */
    public ConcurrentLinkedHashMap<K, V> build() {
      if (capacity < 0) {
        throw new IllegalStateException();
      }
      return new ConcurrentLinkedHashMap<K, V>(this);
    }
  }
}
