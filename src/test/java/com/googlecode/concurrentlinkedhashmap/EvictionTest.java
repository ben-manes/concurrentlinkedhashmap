/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Task;
import com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.EfficiencyRun;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.AMORTIZED_DRAIN_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.BUFFER_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_BUFFER_SIZE;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.bufferIndex;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DrainStatus.REQUIRED;
import static com.googlecode.concurrentlinkedhashmap.EvictionTest.Status.ALIVE;
import static com.googlecode.concurrentlinkedhashmap.EvictionTest.Status.DEAD;
import static com.googlecode.concurrentlinkedhashmap.EvictionTest.Status.RETIRED;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyCollection.emptyCollection;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyMap.emptyMap;
import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.determineEfficiency;
import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Awaitility.to;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A unit-test for the page replacement algorithm and its public methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class EvictionTest extends AbstractTest {

  /* ---------------- Capacity -------------- */

  @Test(dataProvider = "warmedMap")
  public void capacity_increase(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = ImmutableMap.copyOf(newWarmedMap());
    long newMaxCapacity = 2 * capacity();

    map.setCapacity(newMaxCapacity);
    assertThat(map, is(equalTo(expected)));
    assertThat(map.capacity(), is(equalTo(newMaxCapacity)));
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_increaseToMaximum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.setCapacity(MAXIMUM_CAPACITY);
    assertThat(map.capacity(), is(equalTo(MAXIMUM_CAPACITY)));
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_increaseAboveMaximum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.setCapacity(MAXIMUM_CAPACITY + 1);
    assertThat(map.capacity(), is(equalTo(MAXIMUM_CAPACITY)));
  }

  @Test
  public void capacity_decrease() {
    checkDecreasedCapacity(capacity() / 2);
  }

  @Test
  public void capacity_decreaseToMinimum() {
    checkDecreasedCapacity(0);
  }

  private void checkDecreasedCapacity(long newMaxCapacity) {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .listener(listener)
        .build();
    warmUp(map, 0, capacity());
    map.setCapacity(newMaxCapacity);

    assertThat(map, is(valid()));
    assertThat(map.size(), is(equalTo((int) newMaxCapacity)));
    assertThat(map.capacity(), is(equalTo(newMaxCapacity)));
    verify(listener, times((int) (capacity() - newMaxCapacity))).onEviction(anyInt(), anyInt());
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void capacity_decreaseBelowMinimum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    try {
      map.setCapacity(-1);
    } finally {
      assertThat(map.capacity(), is(equalTo(capacity())));
    }
  }

  /* ---------------- Eviction -------------- */

  @Test(dataProvider = "builder", expectedExceptions = IllegalStateException.class)
  public void evict_listenerFails(Builder<Integer, Integer> builder) {
    doThrow(new IllegalStateException()).when(listener).onEviction(anyInt(), anyInt());
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(0)
        .listener(listener)
        .build();
    try {
      warmUp(map, 0, capacity());
    } finally {
      assertThat(map, is(valid()));
    }
  }

  @Test
  public void evict_alwaysDiscard() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(0)
        .listener(listener)
        .build();
    warmUp(map, 0, 100);

    assertThat(map, is(valid()));
    verify(listener, times(100)).onEviction(anyInt(), anyInt());
  }

  @Test
  public void evict() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(10)
        .listener(listener)
        .build();
    warmUp(map, 0, 20);

    assertThat(map, is(valid()));
    assertThat(map.size(), is(10));
    assertThat(map.weightedSize(), is(10L));
    verify(listener, times(10)).onEviction(anyInt(), anyInt());
  }

  @Test(dataProvider = "builder")
  public void evict_weighted(Builder<Integer, Collection<Integer>> builder) {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map = builder
        .weigher(Weighers.<Integer>collection())
        .maximumWeightedCapacity(10)
        .build();

    map.put(1, asList(1, 2));
    map.put(2, asList(3, 4, 5, 6, 7));
    map.put(3, asList(8, 9, 10));
    assertThat(map.weightedSize(), is(10L));

    // evict (1)
    map.put(4, asList(11));
    assertThat(map.containsKey(1), is(false));
    assertThat(map.weightedSize(), is(9L));

    // evict (2, 3)
    map.put(5, asList(12, 13, 14, 15, 16, 17, 18, 19, 20));
    assertThat(map.weightedSize(), is(10L));

    assertThat(map, is(valid()));
  }

  @Test(dataProvider = "builder")
  public void evict_maximumCapacity(Builder<Integer, Integer> builder) {
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(MAXIMUM_CAPACITY)
        .build();
    map.put(1, 2);
    map.capacity = MAXIMUM_CAPACITY;
    map.weightedSize.set(MAXIMUM_CAPACITY);

    map.put(2, 3);
    assertThat(map.weightedSize(), is(MAXIMUM_CAPACITY));
    assertThat(map, is(equalTo(singletonMap(2, 3))));
  }

  @Test
  public void evict_alreadyRemoved() throws Exception {
    final ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(1)
        .listener(listener)
        .build();
    map.put(0, 0);
    map.evictionLock.lock();
    try {
      Node<Integer, Integer> node = map.data.get(0);
      checkStatus(map, node, ALIVE);
      new Thread() {
        @Override public void run() {
          map.put(1, 1);
          assertThat(map.remove(0), is(0));
        }
      }.start();
      await().untilCall(to((Map<?, ?>) map).containsKey(0), is(false));
      checkStatus(map, node, RETIRED);
      map.drainBuffers();

      checkStatus(map, node, DEAD);
      assertThat(map.containsKey(1), is(true));
      verify(listener, never()).onEviction(anyInt(), anyInt());
    } finally {
      map.evictionLock.unlock();
    }
  }

  enum Status { ALIVE, RETIRED, DEAD }

  private static void checkStatus(ConcurrentLinkedHashMap<Integer, Integer> map,
      Node<Integer, Integer> node, Status expected) {
    assertThat(node.get().isAlive(), is(expected == ALIVE));
    assertThat(node.get().isRetired(), is(expected == RETIRED));
    assertThat(node.get().isDead(), is(expected == DEAD));

    if (node.get().isRetired() || node.get().isDead()) {
      assertThat(map.tryToRetire(node, node.get()), is(false));
    }
    if (node.get().isDead()) {
      map.makeRetired(node);
      assertThat(node.get().isRetired(), is(false));
    }
  }

  @Test(dataProvider = "builder")
  public void evict_lru(Builder<Integer, Integer> builder) {
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(10)
        .build();
    warmUp(map, 0, 10);
    checkContainsInOrder(map, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    // re-order
    checkReorder(map, asList(0, 1, 2), 3, 4, 5, 6, 7, 8, 9, 0, 1, 2);

    // evict 3, 4, 5
    checkEvict(map, asList(10, 11, 12), 6, 7, 8, 9, 0, 1, 2, 10, 11, 12);

    // re-order
    checkReorder(map, asList(6, 7, 8), 9, 0, 1, 2, 10, 11, 12, 6, 7, 8);

    // evict 9, 0, 1
    checkEvict(map, asList(13, 14, 15), 2, 10, 11, 12, 6, 7, 8, 13, 14, 15);

    assertThat(map, is(valid()));
  }

  private void checkReorder(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.get(i);
    }
    checkContainsInOrder(map, expect);
  }

  private void checkEvict(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.put(i, i);
    }
    checkContainsInOrder(map, expect);
  }

  private void checkContainsInOrder(ConcurrentLinkedHashMap<Integer, Integer> map,
      Integer... expect) {
    map.drainBuffers();
    List<Integer> evictionList = Lists.newArrayList();
    for (Node<Integer, Integer> node : map.evictionDeque) {
      evictionList.add(node.key);
    }
    assertThat(map.size(), is(equalTo(expect.length)));
    assertThat(map.keySet(), containsInAnyOrder(expect));
    assertThat(evictionList, is(equalTo(asList(expect))));
  }

  @Test
  public void evict_efficiency() {
    Map<String, String> expected = new CacheBuilder()
        .maximumCapacity((int) capacity())
        .makeCache(Cache.LinkedHashMap_Lru_Sync);
    Map<String, String> actual = new Builder<String, String>()
        .maximumWeightedCapacity(capacity())
        .build();

    Generator generator = new ScrambledZipfianGenerator(10 * capacity());
    List<String> workingSet = createWorkingSet(generator, 10 * (int) capacity());

    EfficiencyRun runExpected = determineEfficiency(expected, workingSet);
    EfficiencyRun runActual = determineEfficiency(actual, workingSet);

    String reason = String.format("Expected [%s] but was [%s]", runExpected, runActual);
    assertThat(reason, runActual.hitCount, is(runExpected.hitCount));
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onGet(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.get(first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onGetQuietly(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    final Node<Integer, Integer> last = map.evictionDeque.peekLast();

    map.getQuietly(first.key);
    int maxTaskIndex = map.moveTasksFromBuffers(new Task[AMORTIZED_DRAIN_THRESHOLD]);

    assertThat(map.evictionDeque.peekFirst(), is(first));
    assertThat(map.evictionDeque.peekLast(), is(last));
    assertThat(maxTaskIndex, is(-1));
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPutIfAbsent(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.putIfAbsent(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPut(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.put(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplace(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplaceConditionally(
      final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final Node<Integer, Integer> first = map.evictionDeque.peek();
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(first.key, first.key, first.key);
      }
    });
  }

  private void updateRecency(ConcurrentLinkedHashMap<Integer, Integer> map, Runnable operation) {
    Node<Integer, Integer> first = map.evictionDeque.peek();

    operation.run();
    map.drainBuffers();

    assertThat(map.evictionDeque.peekFirst(), is(not(first)));
    assertThat(map.evictionDeque.peekLast(), is(first));
    assertThat(map, is(valid()));
  }

  @Test(dataProvider = "guardedMap")
  public void applyInRecencyOrder(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final int[] expected = { map.nextOrder };
    int maxTasks = 2 * BUFFER_THRESHOLD;

    for (int i = 0; i < maxTasks; i++) {
      final int id = map.nextOrdering();
      Task task = mock(Task.class);
      when(task.getOrder()).thenReturn(id);
      doAnswer(new Answer<Void>() {
        @Override public Void answer(InvocationOnMock invocation) {
          assertThat(id, is(expected[0]++));
          return null;
        }
      }).when(task).run();

      int index = i % map.buffers.length;
      map.buffers[index].add(task);
      map.bufferLengths.getAndIncrement(index);
    }

    map.drainBuffers();
    for (Queue<?> buffer : map.buffers) {
      assertThat(buffer.isEmpty(), is(true));
    }
  }

  @Test(dataProvider = "guardedMap")
  public void exceedsMaximumBufferSize_onRead(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.bufferLengths.set(bufferIndex(), MAXIMUM_BUFFER_SIZE);

    Task task = mock(Task.class);
    map.afterCompletion(task);
    verify(task, never()).run();

    assertThat(map.buffers[bufferIndex()].size(), is(0));
    map.bufferLengths.set(bufferIndex(), 0);
  }

  @Test(dataProvider = "guardedMap")
  public void exceedsMaximumBufferSize_onWrite(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.bufferLengths.set(bufferIndex(), MAXIMUM_BUFFER_SIZE);

    Task task = mock(Task.class);
    when(task.isWrite()).thenReturn(true);
    map.afterCompletion(task);
    verify(task, times(1)).run();

    assertThat(map.buffers[bufferIndex()].size(), is(0));
    map.bufferLengths.set(bufferIndex(), 0);
  }

  @Test(dataProvider = "warmedMap")
  public void drain(ConcurrentLinkedHashMap<Integer, Integer> map) {
    for (int i = 0; i < BUFFER_THRESHOLD; i++) {
      map.get(1);
    }
    int index = bufferIndex();
    assertThat(map.bufferLengths.get(index), is(equalTo(BUFFER_THRESHOLD)));
    map.get(1);
    assertThat(map.bufferLengths.get(index), is(equalTo(0)));
    assertThat(map.tasks, is(equalTo(new Task[AMORTIZED_DRAIN_THRESHOLD])));
  }

  @Test(dataProvider = "guardedMap")
  public void drain_nonblocking(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    final AtomicBoolean done = new AtomicBoolean();
    Thread thread = new Thread() {
      @Override public void run() {
        map.drainStatus.set(REQUIRED);
        map.tryToDrainBuffers();
        done.set(true);
      }
    };
    map.evictionLock.lock();
    try {
      thread.start();
      await().untilTrue(done);
    } finally {
      map.evictionLock.unlock();
    }
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksClear(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.clear();
      }
    });
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksAscendingKeySet(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.ascendingKeySet();
      }
    });
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.ascendingKeySetWithLimit((int) capacity());
      }
    });
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksDescendingKeySet(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.descendingKeySet();
      }
    });
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.descendingKeySetWithLimit((int) capacity());
      }
    });
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksAscendingMap(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.ascendingMap();
      }
    });
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.ascendingMapWithLimit((int) capacity());
      }
    });
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksDescendingMap(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.descendingMap();
      }
    });
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.descendingMapWithLimit((int) capacity());
      }
    });
  }

  @Test(dataProvider = "guardedMap")
  public void drain_blocksCapacity(final ConcurrentLinkedHashMap<Integer, Integer> map)
      throws Exception {
    checkDrainBlocks(map, new Runnable() {
      @Override public void run() {
        map.setCapacity(0);
      }
    });
  }

  void checkDrainBlocks(final ConcurrentLinkedHashMap<Integer, Integer> map, final Runnable task)
      throws Exception {
    final ReentrantLock lock = (ReentrantLock) map.evictionLock;
    final AtomicBoolean done = new AtomicBoolean();
    final Thread thread = new Thread() {
      @Override public void run() {
        map.drainStatus.set(REQUIRED);
        task.run();
        done.set(true);
      }
    };
    lock.lock();
    try {
      thread.start();
      await().until(new Callable<Boolean>() {
        @Override public Boolean call() {
          return lock.hasQueuedThread(thread);
        }
      });
    } finally {
      lock.unlock();
    }
    await().untilTrue(done);
  }

  /* ---------------- Ascending KeySet -------------- */

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySet(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    assertThat(map.ascendingKeySet(), is(equalTo(expected.keySet())));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySet_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    Set<Integer> original = map.ascendingKeySet();
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected.keySet())));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySetWithLimit_greaterThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity() / 2);

    assertThat(map.ascendingKeySetWithLimit((int) capacity() / 2), is(equalTo(expected.keySet())));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySetWithLimit_lessThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    assertThat(map.ascendingKeySetWithLimit((int) capacity() * 2), is(equalTo(expected.keySet())));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySetWithLimit_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity() / 2);

    Set<Integer> original = map.ascendingKeySetWithLimit((int) capacity() / 2);
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected.keySet())));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingKeySetWithLimit_zero(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertThat(map.ascendingKeySetWithLimit(0), is(emptyCollection()));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void ascendingKeySetWithLimit_negative(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.ascendingKeySetWithLimit(-1);
  }

  /* ---------------- Descending KeySet -------------- */

  @Test(dataProvider = "warmedMap")
  public void descendingKeySet(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> expected = newLinkedHashSet();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.add(i);
    }

    assertThat(map.descendingKeySet(), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingKeySet_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> expected = newLinkedHashSet();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.add(i);
    }

    Set<Integer> original = map.descendingKeySet();
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(original)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingKeySetWithLimit_greaterThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> expected = newLinkedHashSet();
    for (int i = (int) capacity() - 1; i >= capacity() / 2; i--) {
      expected.add(i);
    }
    assertThat(map.descendingKeySetWithLimit((int) capacity() / 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingKeySetWithLimit_lessThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> expected = newLinkedHashSet();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.add(i);
    }
    assertThat(map.descendingKeySetWithLimit((int) capacity() * 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingKeySetWithLimit_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> expected = newLinkedHashSet();
    for (int i = (int) capacity() - 1; i >= capacity() / 2; i--) {
      expected.add(i);
    }

    Set<Integer> original = map.descendingKeySetWithLimit((int) capacity() / 2);
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingKeySetWithLimit_zero(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertThat(map.descendingKeySetWithLimit(0), is(emptyCollection()));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void descendingKeySetWithLimit_negative(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.descendingKeySetWithLimit(-1);
  }

  /* ---------------- Ascending Map -------------- */

  @Test(dataProvider = "warmedMap")
  public void ascendingMap(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    assertThat(map.ascendingMap(), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingMap_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    Map<Integer, Integer> original = map.ascendingMap();
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingMapWithLimit_greaterThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity() / 2);

    assertThat(map.ascendingMapWithLimit((int) capacity() / 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingMapWithLimit_lessThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity());

    assertThat(map.ascendingMapWithLimit((int) capacity() * 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingMapWithLimit_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    warmUp(expected, 0, capacity() / 2);

    Map<Integer, Integer> original = map.ascendingMapWithLimit((int) capacity() / 2);
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void ascendingMapWithLimit_zero(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertThat(map.ascendingMapWithLimit(0), is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void ascendingMapWithLimit_negative(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.ascendingMapWithLimit(-1);
  }

  /* ---------------- Descending Map -------------- */

  @Test(dataProvider = "warmedMap")
  public void descendingMap(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.put(i, -i);
    }
    assertThat(map.descendingMap(), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingMap_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.put(i, -i);
    }

    Map<Integer, Integer> original = map.descendingMap();
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingMapWithLimit_greaterThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    for (int i = (int) capacity() - 1; i >= capacity() / 2; i--) {
      expected.put(i, -i);
    }
    assertThat(map.descendingMapWithLimit((int) capacity() / 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingMapWithLimit_lessThan(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    for (int i = (int) capacity() - 1; i >= 0; i--) {
      expected.put(i, -i);
    }
    assertThat(map.descendingMapWithLimit((int) capacity() * 2), is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingMapWithLimit_snapshot(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = newLinkedHashMap();
    for (int i = (int) capacity() - 1; i >= capacity() / 2; i--) {
      expected.put(i, -i);
    }

    Map<Integer, Integer> original = map.descendingMapWithLimit((int) capacity() / 2);
    map.put((int) capacity(), (int) -capacity());

    assertThat(original, is(equalTo(expected)));
  }

  @Test(dataProvider = "warmedMap")
  public void descendingMapWithLimit_zero(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertThat(map.descendingMapWithLimit(0), is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void descendingMapWithLimit_negative(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.descendingMapWithLimit(-1);
  }
}
