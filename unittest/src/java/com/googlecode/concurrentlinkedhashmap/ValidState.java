package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.newSetFromMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Is the {@link ConcurrentLinkedHashMap} in a valid state?
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class ValidState extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {
  private static final boolean exhaustive = BaseTest.booleanProperty("test.exhaustive");

  @Override
  public void describeTo(Description description) {
    description.appendText("state");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    map.tryToDrainEvictionQueues(false);

    matches &= check(map.writeQueue.isEmpty(), "writeQueue", description);
    for (int i = 0; i < map.recencyQueue.length; i++) {
      matches &= check(map.recencyQueue[i].isEmpty(), "recencyQueue", description);
      matches &= check(map.recencyQueueLength.get(i) == 0, "recencyQueue", description);
    }
    for (Lock lock : map.segmentLock) {
      boolean isLocked = !((ReentrantLock) lock).isLocked();
      matches &= check(isLocked, "locked", description);
    }
    matches &= check(map.listenerQueue.isEmpty(), "listenerQueue", description);
    matches &= check(map.data.size() == map.size(), "Inconsistent size", description);
    matches &= check(map.weightedSize() == map.weightedSize, "weightedSize", description);
    matches &= check(map.capacity() == map.maximumWeightedSize, "capacity", description);
    matches &= check(map.maximumWeightedSize >= map.weightedSize(), "overflow", description);
    matches &= check(map.sentinel.prev != null, "link corruption", description);
    matches &= check(map.sentinel.next != null, "link corruption", description);

    if (exhaustive) {
      matches &= checkLinks(map, description);
    }
    return matches;
  }

  /**
   * Validates that the doubly-linked list running through the map is in a
   * correct state.
   */
  @SuppressWarnings("unchecked")
  private boolean checkLinks(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    matches &= checkSentinel(map, description);
    Set<Node> seen = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    Node current = map.sentinel.next;
    while (current != map.sentinel) {
      matches &= check(seen.add(current),
          String.format("Loop detected: %s, saw %s in %s", current, seen, map), description);
      matches &= assertDataNode(map, current, description);
      current = current.next;
    }
    matches &= check(map.size() == seen.size(), "Size != list length", description);
    return matches;
  }

  /** Validates that the sentinel node is in a proper state. */
  public boolean checkSentinel(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    matches &= check(map.sentinel.key == null, "key", description);
    matches &= check(map.sentinel.weightedValue == null, "value", description);
    matches &= check(map.sentinel.segment == -1, "segment", description);
    matches &= check(map.sentinel.prev.next == map.sentinel, "circular", description);
    matches &= check(map.sentinel.next.prev == map.sentinel, "circular", description);
    matches &= check(!map.data.containsValue(map.sentinel), "in map", description);
    return matches;
  }

  /** Validates the the data node is in a proper state. */
  @SuppressWarnings("unchecked")
  private boolean assertDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
      Description description) {
    boolean matches = true;
    matches &= check(node.key != null, "null key", description);
    matches &= check(node.weightedValue != null, "null weighted value", description);
    matches &= check(node.weightedValue.value != null, "null value", description);
    matches &= check(node.weightedValue.weight ==
      ((Weigher) map.weigher).weightOf(node.weightedValue.value), "weight", description);

    matches &= check(map.containsKey(node.key), "inconsistent", description);
    matches &= check(map.containsValue(node.weightedValue.value),
        String.format("Could not find value: %s", node.weightedValue.value), description);
    matches &= check(map.data.get(node.key) == node, "found wrong node", description);
    matches &= check(node.prev != null, "null prev", description);
    matches &= check(node.next != null, "null next", description);
    matches &= check(node != node.prev, "circular node", description);
    matches &= check(node != node.next, "circular node", description);
    matches &= check(node == node.prev.next, "link corruption", description);
    matches &= check(node == node.next.prev, "link corruption", description);
    matches &= check(node.segment == map.segmentFor(node.key), "bad segment", description);
    return matches;
  }

  private boolean check(boolean expression, String errorMsg, Description description) {
    if (!expression) {
      description.appendText(" " + errorMsg);
    }
    return expression;
  }

  /** Matches an empty map. */
  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> valid() {
    return new ValidState();
  }
}
