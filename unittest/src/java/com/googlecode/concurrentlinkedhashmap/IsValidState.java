package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.newSetFromMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A matcher that evaluates a {@link ConcurrentLinkedHashMap} to determine if it
 * is in a valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsValidState extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("state");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    while (hasPendingReorderings(map)) {
      map.tryToDrainEvictionQueues(false);
    }

    for (int i = 0; i < map.recencyQueue.length; i++) {
      matches &= check(map.recencyQueue[i].isEmpty(), "recencyQueue not empty", description);
      matches &= check(map.recencyQueueLength.get(i) == 0, "recencyQueueLength != 0", description);
    }
    matches &= check(map.listenerQueue.isEmpty(), "listenerQueue", description);
    matches &= check(map.data.size() == map.size(), "Inconsistent size", description);
    matches &= check(map.weightedSize() == map.weightedSize, "weightedSize", description);
    matches &= check(map.capacity() == map.maximumWeightedSize, "capacity", description);
    matches &= check(map.maximumWeightedSize >= map.weightedSize(), "overflow", description);
    matches &= check(map.sentinel.prev != null, "link corruption", description);
    matches &= check(map.sentinel.next != null, "link corruption", description);
    if (map.isEmpty()) {
      matches &= new IsEmptyMap().matchesSafely(map, description);
    }
    matches &= checkLinks(map, description);
    matches &= checkLocks(map, description);
    return matches;
  }

  private boolean hasPendingReorderings(ConcurrentLinkedHashMap<?, ?> map) {
    for (int i = 0; i < map.recencyQueue.length; i++) {
      if (map.recencyQueueLength.get(i) != 0) {
        return true;
      }
    }
    return false;
  }

  /** Validates the doubly-linked list. */
  @SuppressWarnings("unchecked")
  private boolean checkLinks(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    int weightedSize = 0;
    boolean matches = true;
    matches &= checkSentinel(map, description);
    Set<Node> seen = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    Node current = map.sentinel.next;
    while (current != map.sentinel) {
      matches &= check(seen.add(current),
          String.format("Loop detected: %s, saw %s in %s", current, seen, map), description);
      matches &= checkDataNode(map, current, description);
      weightedSize += current.getWeightedValue().weight;
      current = current.next;
    }
    matches &= check(map.size() == seen.size(), "Size != list length", description);
    matches &= check(map.weightedSize() == weightedSize, "WeightedSize != link weights"
        + " [" + map.weightedSize() + " vs. " + weightedSize + "]"
        + " {size: " + map.size() + " vs. " + seen.size() + "}",
        description);
    return matches;
  }

  /** Validates the sentinel node. */
  private boolean checkSentinel(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    matches &= check(map.sentinel.key == null, "key", description);
    matches &= check(map.sentinel.getWeightedValue() == null, "value", description);
    matches &= check(map.sentinel.prev.next == map.sentinel, "circular", description);
    matches &= check(map.sentinel.next.prev == map.sentinel, "circular", description);
    matches &= check(!map.data.containsValue(map.sentinel), "in map", description);
    return matches;
  }

  /** Validates the data node. */
  @SuppressWarnings("unchecked")
  private boolean checkDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
      Description description) {
    boolean matches = true;
    matches &= check(node.key != null, "null key", description);
    matches &= check(node.getWeightedValue() != null, "null weighted value", description);
    matches &= check(node.getWeightedValue().value != null, "null value", description);
    matches &= check(node.getWeightedValue().weight ==
      ((Weigher) map.weigher).weightOf(node.getWeightedValue().value), "weight", description);

    matches &= check(map.containsKey(node.key), "inconsistent", description);
    matches &= check(map.containsValue(node.getWeightedValue().value),
        String.format("Could not find value: %s", node.getWeightedValue().value), description);
    matches &= check(map.data.get(node.key) == node, "found wrong node", description);
    matches &= check(node.prev != null, "null prev", description);
    matches &= check(node.next != null, "null next", description);
    matches &= check(node != node.prev, "circular node", description);
    matches &= check(node != node.next, "circular node", description);
    matches &= check(node == node.prev.next, "link corruption", description);
    matches &= check(node == node.next.prev, "link corruption", description);
    return matches;
  }

  /** Validates that the locks are not held. */
  private boolean checkLocks(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean isLocked = ((ReentrantLock) map.evictionLock).isLocked();
    return check(!isLocked, "locked", description);
  }

  private boolean check(boolean expression, String errorMsg, Description description) {
    if (!expression) {
      description.appendText(" " + errorMsg);
    }
    return expression;
  }

  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> valid() {
    return new IsValidState();
  }
}
