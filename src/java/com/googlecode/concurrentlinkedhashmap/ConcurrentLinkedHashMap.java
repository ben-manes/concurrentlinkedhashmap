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
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

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
  // recorded in a buffer. These buffers are drained at the first opportunity
  // after a write or when a buffer exceeds a threshold size. A strict ordering
  // is achieved by observing that each buffer is in a weakly sorted order
  // relative to the last drain. This allows the buffers to be merged in O(n)
  // time so that the operations are run in the expected order.
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

  /** The number of pending operations per buffer before attempting to drain. */
  static final int BUFFER_THRESHOLD = 16;

  /** The number of buffers to use. */
  static final int NUMBER_OF_BUFFERS;

  /** Mask value for indexing into the buffers. */
  static final int BUFFER_MASK;

  /** The maximum number of pending operations to perform per drain. */
  static final int MAXIMUM_OPERATIONS_TO_DRAIN;

  static {
    // Find the power-of-two best matching the number of available processors
    int buffers = 1;
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    while (buffers < availableProcessors) {
      buffers <<= 1;
    }
    MAXIMUM_OPERATIONS_TO_DRAIN = (1 + buffers) * BUFFER_THRESHOLD;
    NUMBER_OF_BUFFERS = buffers;
    BUFFER_MASK = buffers - 1;
  }

  /** An executor that runs the task on the caller's thread. */
  static final Executor sameThreadExecutor = new SameThreadExecutor();

  /** A queue that discards all entries. */
  static final Queue<?> discardingQueue = new DiscardingQueue();

  /** The draining status of the buffers. */
  enum DrainStatus {

    /** A drain is not taking place. */
    IDLE,

    /** A drain is required due to a pending write modification. */
    REQUIRED,

    /** A drain is in progress. */
    PROCESSING
  }

  // The backing data store holding the key-value associations
  final ConcurrentMap<K, Node> data;
  final int concurrencyLevel;

  // These fields provide support to bound the map by a maximum capacity
  @GuardedBy("evictionLock")
  final LinkedDeque<Node> evictionDeque;

  @GuardedBy("evictionLock") // must write under lock
  volatile int weightedSize;
  @GuardedBy("evictionLock") // must write under lock
  volatile int capacity;

  volatile int globalOrder;
  @GuardedBy("evictionLock")
  int drainedOrder;

  final Lock evictionLock;
  final Executor executor;
  final Weigher<? super V> weigher;
  final Queue<Task>[] buffers;
  final AtomicIntegerArray bufferLengths;
  final AtomicReference<DrainStatus> drainStatus;

  // These fields provide support for notifying a listener.
  final Queue<Node> pendingNotifications;
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
    globalOrder = Integer.MIN_VALUE;
    drainedOrder = Integer.MIN_VALUE;
    evictionDeque = new LinkedDeque<Node>();
    drainStatus = new AtomicReference<DrainStatus>(IDLE);

    buffers = (Queue<Task>[]) new Queue[NUMBER_OF_BUFFERS];
    bufferLengths = new AtomicIntegerArray(NUMBER_OF_BUFFERS);
    for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
      buffers[i] = new ConcurrentLinkedQueue<Task>();
    }

    // The notification queue and listener
    listener = builder.listener;
    pendingNotifications = (listener == DiscardingListener.INSTANCE)
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
      drainBuffers();
      evict();
    } finally {
      evictionLock.unlock();
    }
    notifyListener();
  }

  /** Determines whether the map has exceeded its capacity. */
  boolean hasOverflowed() {
    return weightedSize > capacity;
  }

  /**
   * Evicts entries from the map while it exceeds the capacity and appends
   * evicted entries to the notification queue for processing.
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
      Node node = evictionDeque.poll();

      // If weighted values are used, then the pending operations will adjust
      // the size to reflect the correct weight
      if (node == null) {
        assert (weigher != Weighers.singleton());
        return;
      }

      // Notify the listener only if the entry was evicted
      if (data.remove(node.key, node)) {
        pendingNotifications.add(node);
      }

      node.makeDead();
    }
  }

  /**
   * @param task the pending operation to apply
   */
  void afterCompletion(Task task) {
    boolean delayable = schedule(task);
    tryToDrainBuffers(delayable);
    notifyListener();
  }

  /**
   * Schedules the task to be applied to the page replacement policy.
   *
   * @param task the pending operation
   * @return if the buffers should be drained
   */
  private boolean schedule(Task task) {
    int index = bufferIndex();
    int buffered = bufferLengths.incrementAndGet(index);

    // A buffer may discard a read task if its length exceeds a tolerance level
    if ((buffered <= MAXIMUM_BUFFER_SIZE) || task.isWrite()) {
      buffers[index].add(task);
    } else {
      bufferLengths.decrementAndGet(index);
    }


    if (task.isWrite()) {
      drainStatus.set(REQUIRED);
      return false;
    }
    return (buffered <= BUFFER_THRESHOLD);
  }

  /** Returns the index to the buffer that the task should be scheduled on. */
  static int bufferIndex() {
    // A buffer is chosen by the thread's id so that tasks are distributed in a
    // pseudo evenly manner. This helps avoid hot entries causing contention due
    // to other threads trying to append to the same buffer.
    return (int) Thread.currentThread().getId() & BUFFER_MASK;
  }

  /**
   * Attempts to acquire the eviction lock and apply the pending operations to
   * the page replacement policy.
   *
   * @param delayable if a drain should be delayed until required
   */
  void tryToDrainBuffers(boolean delayable) {
    if (!delayable || (drainStatus.get() == REQUIRED)) {
      if (evictionLock.tryLock()) {
        try {
          drainBuffers();
        } finally {
          evictionLock.unlock();
        }
      }
    }
  }

  /** Drains the buffers and applies the pending operations. */
  @GuardedBy("evictionLock")
  void drainBuffers() {
    drainStatus.set(PROCESSING);

    // A strict ordering is achieved by observing that each buffer contains
    // tasks in a weakly sorted order starting from the last drain. The buffers
    // can be merged into a sorted list in O(n) time by using counting sort and
    // chaining on a collision.

    // The output is capped to the expected number of tasks plus additional
    // slack to optimistically handle the concurrent additions to the buffers.
    Task[] tasks = new Task[MAXIMUM_OPERATIONS_TO_DRAIN];

    // Moves the tasks into the output array, applies them, and updates the
    // marker for the starting order of the next drain.
    int maxTaskIndex = moveTasksFromBuffers(tasks);
    runTasks(tasks, maxTaskIndex);
    updateDrainedOrder(tasks, maxTaskIndex);

    drainStatus.compareAndSet(PROCESSING, IDLE);
  }

  /**
   * Moves the tasks from the buffers into the output array.
   *
   * @param tasks the ordered array of the pending operations
   * @return the highest index location of a task that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveTasksFromBuffers(Task[] tasks) {
    int maxTaskIndex = -1;
    for (int i = 0; i < buffers.length; i++) {
      int maxIndex = moveTasksFromBuffer(i, tasks);
      maxTaskIndex = Math.max(maxIndex, maxTaskIndex);
    }
    return maxTaskIndex;
  }

  /**
   * Moves the tasks from the specified buffer into the output array.
   *
   * @param bufferIndex the buffer to drain
   * @param tasks the ordered array of the pending operations
   * @return the highest index location of a task that was added to the array
   */
  @GuardedBy("evictionLock")
  int moveTasksFromBuffer(int bufferIndex, Task[] tasks) {
    // While a buffer is being drained it may be concurrently appended to. The
    // number of tasks removed are tracked so that the length can be decremented
    // by the delta rather than set to zero.
    Queue<Task> buffer = buffers[bufferIndex];
    int removedFromBuffer = 0;

    Task task;
    int maxIndex = -1;
    while ((task = buffer.poll()) != null) {
      removedFromBuffer++;

      // The index into the output array is determined by calculating the offset
      // since the last drain
      int index = task.getOrder() - drainedOrder;
      if (index < 0) {
        // The task was missed by the last drain and can be run immediately
        task.run();
        continue;
      } else if (index >= tasks.length) {
        // Due to concurrent additions, the order exceeds the capacity of the
        // output array. It is added to the end as overflow and the remaining
        // tasks in the buffer will be handled by the next drain.
        maxIndex = tasks.length - 1;
        addTaskToChain(tasks, task, maxIndex);
        break;
      } else {
        maxIndex = Math.max(index, maxIndex);
        addTaskToChain(tasks, task, index);
      }
    }
    bufferLengths.addAndGet(bufferIndex, -removedFromBuffer);
    return maxIndex;
  }

  /**
   * Adds the task as the head of the chain at the index location.
   *
   * @param tasks the ordered array of the pending operations
   * @param task the pending operation to add
   * @param index the array location
   */
  @GuardedBy("evictionLock")
  void addTaskToChain(Task[] tasks, Task task, int index) {
    task.setNext(tasks[index]);
    tasks[index] = task;
  }

  /**
   * Runs the pending tasks to the page replacement policy.
   *
   * @param tasks the ordered array of the pending operations
   * @param maxTaskIndex the maximum index of the array
   */
  @GuardedBy("evictionLock")
  void runTasks(Task[] tasks, int maxTaskIndex) {
    for (int i = 0; i <= maxTaskIndex; i++) {
      runTasksInChain(tasks[i]);
    }
  }

  /**
   * Runs the pending operations on the linked chain.
   *
   * @param head the first task in the chain of operations
   */
  @GuardedBy("evictionLock")
  void runTasksInChain(Task head) {
    Task task = head;
    while (task != null) {
      task.run();
      task = task.getNext();
    }
  }

  /**
   * Updates the order to start the next drain from.
   *
   * @param tasks the ordered array of operations
   * @param maxTaskIndex the maximum index of the array
   */
  @GuardedBy("evictionLock")
  void updateDrainedOrder(Task[] tasks, int maxTaskIndex) {
    if (maxTaskIndex >= 0) {
      Task task = tasks[maxTaskIndex];
      drainedOrder = task.getOrder() + 1;
    }
  }

  /** Returns the ordering value to assign to a task. */
  int nextOrdering() {
    // The global order is acquired in a racy fashion as the increment is not
    // atomic with the insertion into a buffer. This means that concurrent tasks
    // can have the same ordering and the buffers are in a weakly sorted order.
    return globalOrder++;
  }

  /** Notifies the listener of entries that were evicted. */
  void notifyListener() {
    Node node;
    while ((node = pendingNotifications.poll()) != null) {
      listener.onEviction(node.key, node.getValue());
    }
  }

  interface Task extends Runnable {
    int getOrder();
    boolean isWrite();

    Task getNext();
    void setNext(Task task);
  }

  abstract class AbstractTask implements Task {
    final int order;
    Task task;

    AbstractTask() {
      this.order = nextOrdering();
    }

    @Override
    public boolean isWrite() {
      return false;
    }

    @Override
    public int getOrder() {
      return order;
    }

    @Override
    public Task getNext() {
      return task;
    }

    @Override
    public void setNext(Task task) {
      this.task = task;
    }
  }

  /** Updates the node's location in the page replacement policy. */
  class ReadTask extends WeakReference<Node> implements Task {
    final int order;
    Task task;

    ReadTask(Node node) {
      super(node);
      this.order = nextOrdering();
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

    @Override
    public boolean isWrite() {
      return false;
    }

    @Override
    public int getOrder() {
      return order;
    }

    @Override
    public Task getNext() {
      return task;
    }

    @Override
    public void setNext(Task task) {
      this.task = task;
    }
  }

  /** Adds the node to the page replacement policy. */
  final class AddTask extends AbstractTask {
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

      // ignore out-of-order write operations
      if (node.get().isAlive()) {
        evictionDeque.add(node);
        evict();
      }
    }

    @Override
    public boolean isWrite() {
      return true;
    }
  }

  /** Removes a node from the page replacement policy. */
  final class RemovalTask extends AbstractTask {
    final Node node;

    RemovalTask(Node node) {
      this.node = node;
    }

    @Override
    @GuardedBy("evictionLock")
    public void run() {
      evictionDeque.remove(node);
      node.makeDead();
    }

    @Override
    public boolean isWrite() {
      return true;
    }
  }

  /** Updates the weighted size and evicts an entry on overflow. */
  final class UpdateTask extends ReadTask {
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

    @Override
    public boolean isWrite() {
      return true;
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
    // adds unnecessary contention on the eviction lock and buffers.
    evictionLock.lock();
    try {
      Node node;
      while ((node = evictionDeque.poll()) != null) {
        data.remove(node.key, node);
        node.makeDead();
      }

      // Drain the buffers and run only the write tasks
      for (int i = 0; i < buffers.length; i++) {
        Queue<Task> buffer = buffers[i];
        int removed = 0;
        Task task;
        while ((task = buffer.poll()) != null) {
          if (task.isWrite()) {
            task.run();
          }
          removed++;
        }
        bufferLengths.addAndGet(i, -removed);
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
      if (node.getValue().equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    checkNotNull(key, "null key");

    final Node node = data.get(key);
    if (node == null) {
      return null;
    }
    afterCompletion(new ReadTask(node));
    return node.getValue();
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

    final int weight = weigher.weightOf(value);
    final WeightedValue<V> weightedValue = WeightedValue.create(value, weight);
    final Node node = new Node(key, weightedValue);

    for (;;) {
      final Node prior = data.putIfAbsent(node.key, node);
      if (prior == null) {
        afterCompletion(new AddTask(node, weight));
        return null;
      } else if (onlyIfAbsent) {
        afterCompletion(new ReadTask(prior));
        return prior.getValue();
      }
      for (;;) {
        final WeightedValue<V> oldWeightedValue = prior.get();
        if (!oldWeightedValue.isAlive()) {
          break;
        }

        if (prior.compareAndSet(oldWeightedValue, weightedValue)) {
          final int weightedDifference = weight - oldWeightedValue.weight;
          final Task task = (weightedDifference == 0)
              ? new ReadTask(prior)
              : new UpdateTask(prior, weightedDifference);
          afterCompletion(task);
          return oldWeightedValue.value;
        }
      }
    }
  }

  @Override
  public V remove(Object key) {
    checkNotNull(key, "null key");

    final Node node = data.remove(key);
    if (node == null) {
      return null;
    }

    node.makeRetired();
    afterCompletion(new RemovalTask(node));
    return node.getValue();
  }

  @Override
  public boolean remove(Object key, Object value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    Node node = data.get(key);
    if (node == null) {
      return false;
    }

    final WeightedValue<V> weightedValue = node.get();
    if (weightedValue.hasValue(value) && node.tryToRetire(weightedValue)) {
      data.remove(key, node);
      afterCompletion(new RemovalTask(node));
      return true;
    }
    return false;
  }

  @Override
  public V replace(K key, V value) {
    checkNotNull(key, "null key");
    checkNotNull(value, "null value");

    final int weight = weigher.weightOf(value);
    final WeightedValue<V> weightedValue = WeightedValue.create(value, weight);

    for (;;) {
      final Node node = data.get(key);
      if (node == null) {
        return null;
      }
      WeightedValue<V> oldWeightedValue = node.get();
      if (!oldWeightedValue.isAlive()) {
        return null;
      }
      if (node.compareAndSet(oldWeightedValue, weightedValue)) {
        int weightedDifference = weight - oldWeightedValue.weight;
        final Task task = (weightedDifference == 0)
            ? new ReadTask(node)
            : new UpdateTask(node, weightedDifference);
        afterCompletion(task);
        return oldWeightedValue.value;
      }
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkNotNull(key, "null key");
    checkNotNull(oldValue, "null oldValue");
    checkNotNull(newValue, "null newValue");

    final int weight = weigher.weightOf(newValue);
    final WeightedValue<V> newWeightedValue = WeightedValue.create(newValue, weight);

    final Node node = data.get(key);
    if (node == null) {
      return false;
    }
    for (;;) {
      final WeightedValue<V> weightedValue = node.get();
      if (!weightedValue.isAlive() || !weightedValue.hasValue(oldValue)) {
        return false;
      }
      if (node.compareAndSet(weightedValue, newWeightedValue)) {
        int weightedDifference = weight - weightedValue.weight;
        final Task task = (weightedDifference == 0)
            ? new ReadTask(node)
            : new UpdateTask(node, weightedDifference);
        afterCompletion(task);
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
      drainBuffers();

      int size = Math.min(limit, evictionDeque.size());
      Set<K> keys = new LinkedHashSet<K>(size);
      Iterator<Node> iterator = ascending
          ? evictionDeque.iterator()
          : evictionDeque.descendingIterator();
      while (iterator.hasNext() && (limit > keys.size())) {
        keys.add(iterator.next().key);
      }
      return unmodifiableSet(keys);
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
      drainBuffers();

      int size = Math.min(limit, evictionDeque.size());
      Map<K, V> map = new LinkedHashMap<K, V>(size);
      Iterator<Node> iterator = ascending
          ? evictionDeque.iterator()
          : evictionDeque.descendingIterator();
      while (iterator.hasNext() && (limit > map.size())) {
        Node node = iterator.next();
        map.put(node.key, node.getValue());
      }
      return unmodifiableMap(map);
    } finally {
      evictionLock.unlock();
    }
  }

  /** A value, its weight, and its status within the page replacement policy. */
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

    boolean hasValue(Object o) {
      return value.equals(o);
    }

    /** */
    boolean isAlive() {
      return weight > 0;
    }

    boolean isRetired() {
      return weight < 0;
    }

    boolean isDead() {
      return weight == 0;
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

    V getValue() {
      return get().value;
    }

    /** Atomically sets the node to the <tt>retired</tt> state. */
    void makeRetired() {
      for (;;) {
        WeightedValue<V> current = get();
        if (tryToRetire(current)) {
          return;
        }
      }
    }

    /**
     * Attempts to set the node to the <tt>retired</tt> state.
     *
     * @param expect the expected weighted value
     * @return if successful
     */
    boolean tryToRetire(WeightedValue<V> expect) {
      WeightedValue<V> retired = new WeightedValue<V>(expect.value, -expect.weight);
      return compareAndSet(expect, retired);
    }

    /**
     * Atomically sets the node to the <tt>dead</tt> state and decrements the
     * <tt>weightedSize</tt>.
     */
    void makeDead() {
      for (;;) {
        WeightedValue<V> current = get();
        WeightedValue<V> dead = new WeightedValue<V>(current.value, 0);
        if (compareAndSet(current, dead)) {
          weightedSize -= Math.abs(current.weight);
          return;
        }
      }
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
      return (node != null) && (node.getValue().equals(entry.getValue()));
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
      ConcurrentLinkedHashMap.this.remove(current.key, current.getValue());
      current = null;
    }
  }

  /** An entry that allows updates to write through to the map. */
  final class WriteThroughEntry extends SimpleEntry<K, V> {
    static final long serialVersionUID = 1;

    WriteThroughEntry(Node node) {
      super(node.key, node.getValue());
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
  static final class DiscardingQueue extends AbstractQueue<Object> {
    static final Iterator<Object> iter = emptyList().iterator();

    @Override public boolean add(Object e) { return true; }
    @Override public boolean offer(Object e) { return true; }
    @Override public Object poll() { return null; }
    @Override public Object peek() { return null; }
    @Override public int size() { return 0; }
    @Override public Iterator<Object> iterator() { return iter; }
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
   *     .maximumWeightedCapacity(5000)
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
