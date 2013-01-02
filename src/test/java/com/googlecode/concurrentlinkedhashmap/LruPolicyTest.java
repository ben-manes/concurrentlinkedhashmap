/*
 * Copyright 2012 Ben Manes. All Rights Reserved.
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

import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.google.common.collect.Lists;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruPolicy;
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

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.AMORTIZED_DRAIN_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.BUFFER_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.determineEfficiency;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A unit-test for the LRU page replacement policy.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public class LruPolicyTest extends AbstractTest {

  @Test
  public void evict() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
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
    LruPolicy<Integer, Integer> policy = (LruPolicy<Integer, Integer>) map.policy;
    for (Node<Integer, Integer> node : policy.evictionQueue) {
      evictionList.add(node.key);
    }
    assertThat(map.size(), is(equalTo(expect.length)));
    assertThat(map.keySet(), containsInAnyOrder(expect));
    assertThat(evictionList, is(equalTo(asList(expect))));
  }

  @Test
  public void efficiency() {
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

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onGet(final ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    updateRecency(map, policy, new Runnable() {
      @Override public void run() {
        map.get(first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onGetQuietly(final ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    final Node<Integer, Integer> last = policy.evictionQueue.peekLast();

    map.getQuietly(first.key);
    int maxTaskIndex = map.moveTasksFromBuffers(new Task[AMORTIZED_DRAIN_THRESHOLD]);

    assertThat(policy.evictionQueue.peekFirst(), is(first));
    assertThat(policy.evictionQueue.peekLast(), is(last));
    assertThat(maxTaskIndex, is(-1));
  }

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onPutIfAbsent(final ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    updateRecency(map, policy, new Runnable() {
      @Override public void run() {
        map.putIfAbsent(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onPut(final ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    updateRecency(map, policy, new Runnable() {
      @Override public void run() {
        map.put(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onReplace(final ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    updateRecency(map, policy, new Runnable() {
      @Override public void run() {
        map.replace(first.key, first.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap_lru")
  public void updateRecency_onReplaceConditionally(
      final ConcurrentLinkedHashMap<Integer, Integer> map, LruPolicy<Integer, Integer> policy) {
    final Node<Integer, Integer> first = policy.evictionQueue.peek();
    updateRecency(map, policy, new Runnable() {
      @Override public void run() {
        map.replace(first.key, first.key, first.key);
      }
    });
  }

  private void updateRecency(ConcurrentLinkedHashMap<Integer, Integer> map,
      LruPolicy<Integer, Integer> policy, Runnable operation) {
    Node<Integer, Integer> first = policy.evictionQueue.peek();

    operation.run();
    map.drainBuffers();

    assertThat(policy.evictionQueue.peekFirst(), is(not(first)));
    assertThat(policy.evictionQueue.peekLast(), is(first));
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
}
