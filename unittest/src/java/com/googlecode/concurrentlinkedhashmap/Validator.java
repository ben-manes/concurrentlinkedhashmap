package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.Assert;

import static java.lang.String.format;
import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Validations for the concurrent data structure.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class Validator extends Assert {
  private final boolean exhaustive;

  /**
   * A validator for the {@link ConcurrentLinkedHashMap}.
   *
   * @param exhaustive Whether to perform deep validations.
   */
  public Validator(boolean exhaustive) {
    this.exhaustive = exhaustive;
  }

  /**
   * @return Whether in exhaustive validation mode.
   */
  public boolean isExhaustive() {
    return exhaustive;
  }

  /**
   * Validates that the map is in a correct state.
   */
  public void state(ConcurrentLinkedHashMap<?, ?> map) {
    BaseTest.drainEvictionQueues(map);
    assertTrue(map.writeQueue.isEmpty());
    for (int i=0; i<map.reorderQueue.length; i++) {
      assertTrue(map.reorderQueue[i].isEmpty());
      assertEquals(map.reorderQueueLength[i].get(), 0);
    }
    for (Lock lock : map.segmentLock) {
      assertFalse(((ReentrantLock) lock).isLocked());
    }
    assertTrue(map.listenerQueue.isEmpty());

    assertEquals(map.capacity(), map.maximumWeightedSize, "Tracked capacity != reported capacity");
    assertEquals(map.data.size(), map.size(), "Internal size != reported size");
    assertEquals(map.weightedSize(), map.weightedSize,
        "Tracked weighted size != reported weighted size");
    assertTrue(map.maximumWeightedSize >= map.weightedSize());
//    assertNotNull(map.sentinel.prev);
//    assertNotNull(map.sentinel.next.next);

    if (exhaustive) {
      links(map);
    }
  }

  /**
   * Validates that the linked map is empty.
   */
  public void empty(ConcurrentLinkedHashMap<?, ?> map) {
    assertTrue(map.isEmpty(), "Not empty");
    assertTrue(map.data.isEmpty(), "Internal not empty");

    assertEquals(map.size(), 0, "Size != 0");
    assertEquals(map.size(), map.data.size(), "Internal size != 0");

    assertEquals(map.weightedSize, 0, "Weighted size != 0");
    assertEquals(map.weightedSize, 0, "Internal weighted size != 0");

    assertTrue(map.keySet().isEmpty(), "Not empty key set");
    assertTrue(map.values().isEmpty(), "Not empty value set");
    assertTrue(map.entrySet().isEmpty(), "Not empty entry set");
    assertEquals(map, Collections.emptyMap(), "Not equal to empty map");
    assertEquals(map.hashCode(), Collections.emptyMap().hashCode(), "Not equal hash codes");
    assertEquals(map.toString(), Collections.emptyMap().toString(),
        "Not equal string representations");
  }

  /**
   * Validates that the doubly-linked list running through the map is in a correct state.
   */
  private void links(ConcurrentLinkedHashMap<?, ?> map) {
//    assertSentinel(map);
//
//    Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
//    Node current = map.sentinel.next;
//    while (current != map.sentinel) {
//      assertTrue(seen.add(current), format("Loop detected: %s, saw %s in %s", current, seen, map));
//      assertDataNode(map, current);
//      current = current.next;
//    }
//    assertEquals(map.size(), seen.size(), "Size != list length");
  }

  /**
   * Validates that the sentinel node is in a proper state.
   */
  public void assertSentinel(ConcurrentLinkedHashMap<?, ?> map) {
    assertNull(map.sentinel.key);
    assertNull(map.sentinel.weightedValue);
    assertEquals(map.sentinel.segment, -1);
//    assertSame(map.sentinel.prev.next, map.sentinel);
//    assertSame(map.sentinel.next.prev, map.sentinel);
    assertFalse(map.data.containsValue(map.sentinel));
  }

  /**
   * Validates the the data node is in a proper state.
   *
   * @param node The data node.
   */
  private void assertDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node) {
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
//    assertNotNull(node.prev);
//    assertNotNull(node.next);
//    assertNotSame(node, node.prev);
//    assertNotSame(node, node.next);
//    assertSame(node, node.prev.next);
//    assertSame(node, node.next.prev);
    assertEquals(node.segment, map.segmentFor(node.key));
  }
}
