package com.googlecode.concurrentlinkedhashmap;

import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A unit-test for the page replacement algorithm and its public methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class EvictionTest extends BaseTest {

  @Override
  protected int capacity() {
    return 100;
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_increase(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = ImmutableMap.copyOf(newWarmedMap());

    int newMaxCapacity = 2 * capacity();
    map.setCapacity(newMaxCapacity);
    assertEquals(map.capacity(), newMaxCapacity);
    assertEquals(map, expected);
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_decrease(ConcurrentLinkedHashMap<Integer, Integer> map) {
    int newMaxCapacity = capacity() / 2;
    map.setCapacity(newMaxCapacity);
    assertEquals(map.capacity(), newMaxCapacity);
    assertEquals(map.size(), newMaxCapacity);
    assertEquals(map.weightedSize(), newMaxCapacity);
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_decreaseToMinimum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    int newMaxCapacity = 0;
    map.setCapacity(newMaxCapacity);
    assertEquals(map.capacity(), newMaxCapacity);
    assertEquals(map.size(), newMaxCapacity);
    assertEquals(map.weightedSize(), newMaxCapacity);
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_decreaseBelowMinimum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    try {
      map.setCapacity(-1);
      fail("Capacity must be positive");
    } catch (IllegalArgumentException e) {
      assertEquals(map.capacity(), capacity());
    }
  }

  @Test(dataProvider = "collectingListener")
  public void evict_alwaysDiscard(CollectingListener<Integer, Integer> listener) {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(0)
        .listener(listener)
        .build();
    warmUp(map, 0, 100);
    assertEquals(listener.evicted.size(), 100);
  }

  @Test(dataProvider = "collectingListener")
  public void evict(CollectingListener<Integer, Integer> listener) {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(10)
        .listener(listener)
        .build();
    warmUp(map, 0, 20);
    assertEquals(map.size(), 10);
    assertEquals(map.weightedSize(), 10);
    assertEquals(listener.evicted.size(), 10);
  }

  @Test
  public void evict_weighted() {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
        .weigher(Weighers.<Integer>collection())
        .maximumWeightedCapacity(10)
        .build();

    map.put(1, asList(1, 2));
    map.put(2, asList(3, 4, 5, 6, 7));
    map.put(3, asList(8, 9, 10));
    assertEquals(map.weightedSize(), 10);

    // evict (1)
    map.put(4, asList(11));
    assertFalse(map.containsKey(1));
    assertEquals(map.weightedSize(), 9);

    // evict (2, 3)
    map.put(5, asList(12, 13, 14, 15, 16, 17, 18, 19, 20));
    assertEquals(map.weightedSize(), 10, map.toString());
  }

  @Test
  public void evict_lru() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(10)
        .build();
    warmUp(map, 0, 10);
    assertSetEquals(map.keySet(), 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    // re-order
    checkReorder(map, asList(0, 1, 2), 3, 4, 5, 6, 7, 8, 9, 0, 1, 2);

    // evict 3, 4, 5
    checkEvict(map, asList(10, 11, 12), 6, 7, 8, 9, 0, 1, 2, 10, 11, 12);

    // re-order
    checkReorder(map, asList(6, 7, 8), 9, 0, 1, 2, 10, 11, 12, 6, 7, 8);

    // evict 9, 0, 1
    checkEvict(map, asList(13, 14, 15), 2, 10, 11, 12, 6, 7, 8, 13, 14, 15);
  }

  private void checkReorder(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.get(i);
      map.tryToDrainEvictionQueues(false);
    }
    assertEquals(map.keySet(), ImmutableSet.copyOf(expect));
  }

  private void checkEvict(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.put(i, i);
      map.tryToDrainEvictionQueues(false);
    }
    assertSetEquals(map.keySet(), expect);
  }

  private <E> void assertSetEquals(Set<E> set, E... other) {
    assertEquals(set, ImmutableSet.copyOf(other));
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onGet(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.get(originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPutIfAbsent(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.putIfAbsent(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPut(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.put(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplace(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplaceConditionally(
      final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(originalHead.key, originalHead.key, originalHead.key);
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void updateRecency(ConcurrentLinkedHashMap<?, ?> map, Runnable operation) {
    Node originalHead = map.sentinel.next;
    int length = listLength(map);
    int size = map.size();

    operation.run();
    map.drainRecencyQueues();

    assertNotSame(map.sentinel.next, originalHead);
    assertSame(map.sentinel.prev, originalHead);
    assertEquals(listLength(map), length);
    assertEquals(map.size(), size);
  }
}
