package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import java.util.Collection;

/**
 * A unit-test for the page replacement algorithm and its public methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class EvictionTest extends BaseTest {

  @Test(groups = "development")
  public void capacity() {
    debug(" * capacity: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

    int newMaxCapacity = 2 * capacity;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache, createWarmedMap());
    validator.state(cache);
    debug("capacity: #1 done");

    newMaxCapacity = capacity / 2;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache.size(), newMaxCapacity);
    assertEquals(cache.weightedSize(), newMaxCapacity);
    validator.state(cache);
    debug("capacity: #2 done");

    newMaxCapacity = 1;
    cache.setCapacity(newMaxCapacity);
    assertEquals(cache.capacity(), newMaxCapacity);
    assertEquals(cache.size(), newMaxCapacity);
    assertEquals(cache.weightedSize(), newMaxCapacity);
    validator.state(cache);
    debug("capacity: #3 done");

    try {
      cache.setCapacity(-1);
      fail("Capacity must be positive");
    } catch (Exception e) {
      assertEquals(cache.capacity(), newMaxCapacity);
    }
  }

  @Test(groups = "development")
  public void alwaysDiscard() {
    debug(" * alwaysDiscard: START");
    EvictionMonitor monitor = EvictionMonitor.newMonitor();
    ConcurrentLinkedHashMap<Integer, Integer> cache = create(0, monitor);
    for (int i=0; i<100; i++) {
      assertNull(cache.put(i, i));
    }
    assertEquals(monitor.evicted.size(), 100);
  }

  @Test(groups = "development")
  public void boundSize() {
    debug(" * boundSize: START");
    EvictionMonitor monitor = EvictionMonitor.newMonitor();
    ConcurrentLinkedHashMap<Integer, Integer> cache = create(10, monitor);
    for (int i=0; i<20; i++) {
      cache.put(i, i);
    }
    assertEquals(cache.size(), 10);
    assertEquals(cache.weightedSize(), 10);
    assertEquals(monitor.evicted.size(), 10);
  }

  //TODO(bmanes): Fix and enable
  @Test(enabled = false, groups = "development")
  public void weightedBoundSize() {
    debug(" * boundSize: START");
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

  //TODO(bmanes): Rewrite as LIRS tests

//  @Test(groups = "development")
//  public void lruOnAccess() {
//    debug(" * lruOnAccess: START");
//    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(50);
//    Node headNode = cache.sentinel.next;
//    Node tailNode = cache.sentinel.prev;
//    int length = listLength(cache);
//    int size = cache.data.size();
//    debug("size: %s, length: %s, writes: %s", size, length, cache.writeQueue.size());
//    debug("init: " + listForwardToString(cache));
//
//    // Get
//    cache.get(headNode.key);
//    drainEvictionQueues(cache);
//    assertNotSame(cache.sentinel.next, headNode);
//    assertSame(cache.sentinel.prev, headNode);
//    assertEquals(listLength(cache), length);
//    assertEquals(cache.data.size(), size);
//    debug("after get:(" + headNode.key + "):" + listForwardToString(cache));
//
//    // PutIfAbsent
//    assertNotSame(cache.sentinel.prev, tailNode,
//        nodeToString(cache.sentinel.prev) + "!=" + nodeToString(tailNode)); // due to get()
//    cache.putIfAbsent((Integer) tailNode.key, (Integer) tailNode.weightedValue.value);
//    drainEvictionQueues(cache);
//
//    assertEquals(listLength(cache), length);
//    assertSame(cache.sentinel.prev, tailNode);
//    assertEquals(cache.data.size(), size);
//  }
//
//  @Test(groups = "development")
//  public void lruEviction() {
//    debug(" * Lru-eviction: START");
//    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(10);
//
//    debug("Initial: %s", listForwardToString(cache));
//    assertTrue(cache.keySet().containsAll(asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
//               "Instead: " + cache.keySet());
//    assertEquals(cache.size(), 10);
//
//    // re-order
//    for (int i : asList(0, 1, 2)) {
//      cache.get(i);
//      drainEvictionQueues(cache);
//    }
//
//    debug("Reordered #1: %s", listForwardToString(cache));
//    assertTrue(cache.keySet().containsAll(asList(3, 4, 5, 6, 7, 8, 9, 0, 1, 2)),
//               "Instead: " + cache.keySet());
//    assertEquals(cache.size(), 10);
//
//    // evict 3, 4, 5
//    for (int i : asList(10, 11, 12)) {
//      cache.put(i, i);
//      drainEvictionQueues(cache);
//    }
//
//    debug("Evict #1: %s", listForwardToString(cache));
//    assertTrue(cache.keySet().containsAll(asList(6, 7, 8, 9, 0, 1, 2, 10, 11, 12)),
//               "Instead: " + cache.keySet());
//    assertEquals(cache.size(), 10);
//
//    // re-order
//    for (int i : asList(6, 7, 8)) {
//      cache.get(i);
//      drainEvictionQueues(cache);
//    }
//
//    debug("Reordered #2: %s", listForwardToString(cache));
//    assertTrue(cache.keySet().containsAll(asList(9, 0, 1, 2, 10, 11, 12, 6, 7, 8)),
//               "Instead: " + cache.keySet());
//    assertEquals(cache.size(), 10);
//
//    // evict 9, 0, 1
//    for (int i : asList(13, 14, 15)) {
//      cache.put(i, i);
//      drainEvictionQueues(cache);
//    }
//
//    debug("Evict #2: %s", listForwardToString(cache));
//    assertTrue(cache.keySet().containsAll(asList(2, 10, 11, 12, 6, 7, 8, 13, 14, 15)),
//               "Instead: " + cache.keySet());
//    assertEquals(cache.size(), 10);
//  }
}
