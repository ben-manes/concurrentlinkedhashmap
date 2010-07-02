package com.googlecode.concurrentlinkedhashmap;

import static java.util.Arrays.asList;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.annotations.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A unit-test for the page replacement algorithm and its public methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class EvictionTest extends BaseTest {

  @Test(groups = "development")
  public void capacity_increase() {
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

    int newMaxCapacity = 2 * capacity;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache, createWarmedMap());
    validator.checkValidState(cache);
  }

  @Test(groups = "development")
  public void capacity_decrease() {
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

    int newMaxCapacity = capacity / 2;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache.size(), newMaxCapacity);
    assertEquals(cache.weightedSize(), newMaxCapacity);
    validator.checkValidState(cache);
  }

  @Test(groups = "development")
  public void capacity_decreaseToMinimum() {
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

    int newMaxCapacity = 0;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache.size(), newMaxCapacity);
    assertEquals(cache.weightedSize(), newMaxCapacity);
    validator.checkValidState(cache);
  }

  @Test(groups = "development")
  public void capacity_decreaseBelowMinimum() {
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

    try {
      cache.setCapacity(-1);
      fail("Capacity must be positive");
    } catch (Exception e) {
      assertEquals(cache.capacity(), capacity);
    }
  }

  @Test(groups = "development")
  public void evict_alwaysDiscard() {
    EvictionMonitor monitor = EvictionMonitor.newMonitor();
    ConcurrentLinkedHashMap<Integer, Integer> cache = create(0, monitor);
    for (int i=0; i<100; i++) {
      assertNull(cache.put(i, i));
    }
    assertEquals(monitor.evicted.size(), 100);
  }

  @Test(groups = "development")
  public void evict() {
    EvictionMonitor monitor = EvictionMonitor.newMonitor();
    ConcurrentLinkedHashMap<Integer, Integer> cache = create(10, monitor);
    for (int i=0; i<20; i++) {
      cache.put(i, i);
    }
    assertEquals(cache.size(), 10);
    assertEquals(cache.weightedSize(), 10);
    assertEquals(monitor.evicted.size(), 10);
  }

  @Test(groups = "development")
  public void evict_weighted() {
    Builder<Integer, Collection<Integer>> builder = builder();
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> cache = builder
        .weigher(Weighers.<Integer>collection())
        .maximumWeightedCapacity(10)
        .build();

    cache.put(1, asList(1, 2));
    cache.put(2, asList(3, 4, 5, 6, 7));
    cache.put(3, asList(8, 9, 10));
    assertEquals(cache.weightedSize(), 10);

    // evict (1)
    cache.put(4, asList(11));
    assertFalse(cache.containsKey(1));
    assertEquals(cache.weightedSize(), 9);

    // evict (2, 3)
    cache.put(5, asList(12, 13, 14, 15, 16, 17, 18, 19, 20));
    assertEquals(cache.weightedSize(), 10, cache.toString());
  }

  @Test(groups = "development")
  public void evict_lru() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap(10);
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
    assertEquals(map.keySet(), new HashSet<Integer>(asList(expect)));
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
    assertEquals(set, new HashSet<E>(asList(other)));
  }

  @Test(groups = "development")
  public void updateRecency_onGet() {
    final ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = cache.sentinel.next;
    updateRecency(cache, new Runnable() {
      @Override public void run() {
        cache.get(originalHead.key);
      }
    });
  }

  @Test(groups = "development")
  public void updateRecency_onPutIfAbsent() {
    final ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = cache.sentinel.next;
    updateRecency(cache, new Runnable() {
      @Override public void run() {
        cache.putIfAbsent(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(groups = "development")
  public void updateRecency_onPut() {
    final ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = cache.sentinel.next;
    updateRecency(cache, new Runnable() {
      @Override public void run() {
        cache.put(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(groups = "development")
  public void updateRecency_onReplace() {
    final ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = cache.sentinel.next;
    updateRecency(cache, new Runnable() {
      @Override public void run() {
        cache.replace(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(groups = "development")
  public void updateRecency_onReplaceConditionally() {
    final ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = cache.sentinel.next;
    updateRecency(cache, new Runnable() {
      @Override public void run() {
        cache.replace(originalHead.key, originalHead.key, originalHead.key);
      }
    });
  }

  private void updateRecency(ConcurrentLinkedHashMap<?, ?> cache, Runnable operation) {
    Node originalHead = cache.sentinel.next;
    int length = listLength(cache);
    int size = cache.size();

    operation.run();
    cache.drainRecencyQueues();

    assertNotSame(cache.sentinel.next, originalHead);
    assertSame(cache.sentinel.prev, originalHead);
    assertEquals(listLength(cache), length);
    assertEquals(cache.size(), size);
  }
}
