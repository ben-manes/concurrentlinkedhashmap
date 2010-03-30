package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import org.testng.Assert;

import static java.lang.String.format;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Validations for the concurrent data structure.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
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
  public void state(ConcurrentLinkedHashMap<?, ?> map) {
    assertEquals(map.capacity(), map.capacity, "Tracked capacity != reported capacity");
    assertTrue(dequeLength(map) <= map.capacity,
               "The list size is greater than the capacity: "
               + dequeLength(map) + "/" + map.capacity);
    assertEquals(map.data.size(), map.size(), "Internal size != reported size");
    assertTrue(map.capacity() >= map.size(),
               format("Overflow: c=%d s=%d", map.capacity(), map.size()));
    assertNotNull(map.sentinel.prev);
    assertNotNull(map.sentinel.next);

    if (exhaustive) {
      links(map);
    }
  }

  /**
   * Validates that the linked map is empty.
   */
  public void empty(ConcurrentLinkedHashMap<?, ?> map) {
    assertTrue(map.isEmpty(), "Not empty");
    assertTrue(map.data.isEmpty(), "Internel not empty");

    assertEquals(map.size(), 0, "Size != 0");
    assertEquals(map.size(), map.data.size(), "Internel size != 0");

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
    sentinelNode(map);

    Map<Node<?, ?>, Object> seen = new IdentityHashMap<Node<?, ?>, Object>();
    Node<?, ?> current = map.sentinel.next;
    Object dummy = new Object();
    for (; ;) {
      assertNull(seen.put(current, dummy), "Loop detected in list: " + current + " seen: " + seen);
      if (current == map.sentinel) {
        break;
      }
      dataNode(map, current);
      current = current.next;
    }
    assertEquals(map.size(), seen.size() - 1, "Size != list length");
  }

  /**
   * Validates that the sentinel node is in a proper state.
   */
  public void sentinelNode(ConcurrentLinkedHashMap<?, ?> map) {
    assertNull(map.sentinel.key);
    assertNull(map.sentinel.value);
    assertFalse(map.data.containsValue(map.sentinel));
  }

  /**
   * Validates the the data node is in a proper state.
   *
   * @param node The data node.
   */
  public void dataNode(ConcurrentLinkedHashMap<?, ?> map, Node<?, ?> node) {
    assertNotNull(node.key);
    assertNotNull(node.value);
    assertTrue(map.containsKey(node.key));
    assertTrue(map.containsValue(node.value), format("Could not find value: %s", node.value));
    assertEquals(map.data.get(node.key).value, node.value);
    assertSame(map.data.get(node.key), node);
    assertNotNull(node.prev);
    assertNotNull(node.next);
    assertNotSame(node, node.prev);
    assertNotSame(node, node.next);
    assertSame(node, node.prev.next);
    assertSame(node, node.next.prev);
  }

  /**
   * A string representation of the map's list nodes in the list's current order.
   */
  public String printFwd(ConcurrentLinkedHashMap<?, ?> map) {
    Map<Node<?, ?>, Object> seen = new IdentityHashMap<Node<?, ?>, Object>();
    StringBuilder buffer = new StringBuilder("\n");
    Node<?, ?> current = map.sentinel;
    do {
      current = current.next;
      if (seen.put(current, new Object()) != null) {
        buffer.append("Failure: Loop detected\n");
        break;
      }
      buffer.append(current).append("\n");
    } while (current != map.sentinel);
    return buffer.toString();
  }

  /**
   * A string representation of the map's list nodes in the list's current order.
   */
  public String printBwd(ConcurrentLinkedHashMap<?, ?> map) {
    StringBuilder buffer = new StringBuilder("\n");
    Node<?, ?> current = map.sentinel;
    do {
      current = current.prev;
      buffer.append(current).append("\n");
      if (current == null) {
        buffer.append("Failure: Reached null\n");
        break;
      }
    } while (current != map.sentinel);
    return buffer.toString();
  }

  /**
   * A string representation of the node in the map, found by walking the list.
   */
  public String printNode(Object key, ConcurrentLinkedHashMap<?, ?> map) {
    Node<?, ?> current = map.sentinel;
    while (current.key != key) {
      current = current.next;
      if (current == null) {
        return null;
      }
    }
    return current.toString();
  }

  public int pendingReorders(ConcurrentLinkedHashMap<?, ?> map) {
    int buffered = 0;
    for (Queue<?> reorderQueue : map.reorderQueue) {
      buffered += reorderQueue.size();
    }
    return buffered;
  }

  public int dequeLength(ConcurrentLinkedHashMap<?, ?> map) {
    map.evictionLock.lock();
    try {
      return map.length;
    } finally {
      map.evictionLock.unlock();
    }
  }

  public void drainEvictionQueues(ConcurrentLinkedHashMap<?, ?> map) {
    map.evictionLock.lock();
    try {
      for (int i=0; i<map.segments; i++) {
        map.drainReorderQueue(i);
      }
      map.drainWriteQueue();
    } finally {
      map.evictionLock.unlock();
    }
  }
}
