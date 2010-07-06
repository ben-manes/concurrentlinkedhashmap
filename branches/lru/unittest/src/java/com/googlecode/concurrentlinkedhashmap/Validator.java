package com.googlecode.concurrentlinkedhashmap;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.newSetFromMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Validations for the concurrent data structure.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class Validator {
  private static final boolean exhaustive;

  static {
    exhaustive = BaseTest.booleanProperty("test.exhaustive");
  }

  private Validator() {}

  /**
   * Validates that the map is in a correct state.
   */
  public static void checkValidState(ConcurrentLinkedHashMap<?, ?> map) {
    map.tryToDrainEvictionQueues(false);
    assertTrue(map.writeQueue.isEmpty());
    for (int i=0; i<map.recencyQueue.length; i++) {
      assertTrue(map.recencyQueue[i].isEmpty());
      assertEquals(map.recencyQueueLength.get(i), 0);
    }
    for (Lock lock : map.segmentLock) {
      assertFalse(((ReentrantLock) lock).isLocked());
    }
    assertTrue(map.listenerQueue.isEmpty());

    assertEquals(map.data.size(), map.size());
    assertEquals(map.weightedSize(), map.weightedSize);
    assertEquals(map.capacity(), map.maximumWeightedSize);
    assertTrue(map.maximumWeightedSize >= map.weightedSize());
    assertNotNull(map.sentinel.prev);
    assertNotNull(map.sentinel.next.next);
    checkEqualsAndHashCode(map, map);

    if (exhaustive) {
      links(map);
    }
  }

  /**
   * Validates that the linked map is empty.
   */
  public static void checkEmpty(ConcurrentLinkedHashMap<?, ?> map) {
    assertTrue(map.isEmpty(), "Not empty");
    assertTrue(map.data.isEmpty(), "Internal not empty");

    assertEquals(map.size(), 0, "Size != 0");
    assertEquals(map.size(), map.data.size(), "Internal size != 0");

    assertEquals(map.weightedSize(), 0, "Weighted size != 0");
    assertEquals(map.weightedSize, 0, "Internal weighted size != 0");

    checkEmpty(map.keySet());
    checkEmpty(map.values());
    checkEmpty(map.entrySet());
    assertEquals(map, emptyMap(), "Not equal to empty map");
    assertEquals(map.hashCode(), emptyMap().hashCode());
    assertEquals(map.toString(), emptyMap().toString());
  }

  public static void checkEmpty(Collection<?> collection) {
    assertTrue(collection.isEmpty());
    assertEquals(0, collection.size());
    assertFalse(collection.iterator().hasNext());
    assertEquals(0, collection.toArray().length);
    assertEquals(0, collection.toArray(new Object[0]).length);
    if (collection instanceof Set<?>) {
      checkEqualsAndHashCode(emptySet(), collection);
    } else if (collection instanceof List<?>) {
      checkEqualsAndHashCode(emptySet(), collection);
    }
  }

  public static void checkEqualsAndHashCode(Object o1, Object o2) {
    assertEquals(o1, o2);
    assertEquals(o2, o1);
    if (o1 != null) {
      assertEquals(o1.hashCode(), o2.hashCode());
    }
  }

  /**
   * Validates that the doubly-linked list running through the map is in a
   * correct state.
   */
  @SuppressWarnings("unchecked")
  private static void links(ConcurrentLinkedHashMap<?, ?> map) {
    assertSentinel(map);

    Set<Node> seen = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    Node current = map.sentinel.next;
    while (current != map.sentinel) {
      assertTrue(seen.add(current), format("Loop detected: %s, saw %s in %s", current, seen, map));
      assertDataNode(map, current);
      current = current.next;
    }
    assertEquals(map.size(), seen.size(), "Size != list length");
  }

  /**
   * Validates that the sentinel node is in a proper state.
   */
  public static void assertSentinel(ConcurrentLinkedHashMap<?, ?> map) {
    assertNull(map.sentinel.key);
    assertNull(map.sentinel.weightedValue);
    assertEquals(map.sentinel.segment, -1);
    assertSame(map.sentinel.prev.next, map.sentinel);
    assertSame(map.sentinel.next.prev, map.sentinel);
    assertFalse(map.data.containsValue(map.sentinel));
  }

  /**
   * Validates the the data node is in a proper state.
   *
   * @param node The data node.
   */
  @SuppressWarnings("unchecked")
  private static void assertDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node) {
    assertNotNull(node.key);
    assertNotNull(node.weightedValue);
    assertNotNull(node.weightedValue.value);
    assertEquals(node.weightedValue.weight,
        ((Weigher) map.weigher).weightOf(node.weightedValue.value));

    assertTrue(map.containsKey(node.key));
    assertTrue(map.containsValue(node.weightedValue.value),
        format("Could not find value: %s", node.weightedValue.value));
    assertEquals(map.data.get(node.key).weightedValue.value, node.weightedValue.value);
    assertSame(map.data.get(node.key), node);
    assertNotNull(node.prev);
    assertNotNull(node.next);
    assertNotSame(node, node.prev);
    assertNotSame(node, node.next);
    assertSame(node, node.prev.next);
    assertSame(node, node.next.prev);
    assertEquals(node.segment, map.segmentFor(node.key));
  }
}
