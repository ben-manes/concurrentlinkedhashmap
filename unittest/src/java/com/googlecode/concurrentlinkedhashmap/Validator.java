package com.googlecode.concurrentlinkedhashmap;

import static java.lang.String.format;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.Assert;

import java.util.Collection;
import java.util.Collections;
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
  public void checkValidState(ConcurrentLinkedHashMap<?, ?> map) {
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

    assertEquals(map.capacity(), map.maximumWeightedSize, "Tracked capacity != reported capacity");
    assertEquals(map.data.size(), map.size(), "Internal size != reported size");
    assertEquals(map.weightedSize(), map.weightedSize,
        "Tracked weighted size != reported weighted size");
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
  public void checkEmpty(ConcurrentLinkedHashMap<?, ?> map) {
    assertTrue(map.isEmpty(), "Not empty");
    assertTrue(map.data.isEmpty(), "Internal not empty");

    assertEquals(map.size(), 0, "Size != 0");
    assertEquals(map.size(), map.data.size(), "Internal size != 0");

    assertEquals(map.weightedSize(), 0, "Weighted size != 0");
    assertEquals(map.weightedSize, 0, "Internal weighted size != 0");

    checkEmpty(map.keySet());
    checkEmpty(map.values());
    checkEmpty(map.entrySet());
    assertEquals(map, Collections.emptyMap(), "Not equal to empty map");
    assertEquals(map.hashCode(), Collections.emptyMap().hashCode(), "Not equal hash codes");
    assertEquals(map.toString(), Collections.emptyMap().toString(),
        "Not equal string representations");
  }

  public void checkEmpty(Collection<?> collection) {
    assertTrue(collection.isEmpty());
    assertEquals(0, collection.size());
    assertFalse(collection.iterator().hasNext());
    assertEquals(0, collection.toArray().length);
    assertEquals(0, collection.toArray(new Object[0]).length);
    if (collection instanceof Set<?>) {
      checkEqualsAndHashCode(Collections.emptySet(), collection);
    } else if (collection instanceof List<?>) {
      checkEqualsAndHashCode(Collections.emptySet(), collection);
    }
  }

  public void checkEqualsAndHashCode(Object o1, Object o2) {
    assertEquals(o1, o2);
    assertEquals(o2, o1);
    if (o1 != null) {
      assertEquals(o1.hashCode(), o2.hashCode());
    }
  }

  /**
   * Validates that the doubly-linked list running through the map is in a correct state.
   */
  @SuppressWarnings("unchecked")
  private void links(ConcurrentLinkedHashMap<?, ?> map) {
    assertSentinel(map);

    Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
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
  public void assertSentinel(ConcurrentLinkedHashMap<?, ?> map) {
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
    assertNotNull(node.prev);
    assertNotNull(node.next);
    assertNotSame(node, node.prev);
    assertNotSame(node, node.next);
    assertSame(node, node.prev.next);
    assertSame(node, node.next.prev);
    assertEquals(node.segment, map.segmentFor(node.key));
  }
}
