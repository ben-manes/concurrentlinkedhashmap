package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.newSetFromMap;

import com.google.common.collect.Lists;

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

    matches &= check(map.writeQueue.isEmpty(), "writeQueue not empty", description);
    for (int i = 0; i < map.recencyQueue.length; i++) {
      matches &= check(map.recencyQueue[i].isEmpty(), "recencyQueue not empty", description);
      matches &= check(map.recencyQueueLength.get(i) == 0, "recencyQueueLength != 0", description);
    }
    matches &= check(map.listenerQueue.isEmpty(), "listenerQueue", description);
    matches &= check(map.data.size() == map.size(), "Inconsistent size", description);
    matches &= check(map.weightedSize() == map.weightedSize, "weightedSize", description);
    matches &= check(map.capacity() == map.maximumWeightedSize, "capacity", description);
    matches &= check(map.maximumWeightedSize >= map.weightedSize(), "overflow", description);
    matches &= check(map.sentinel.prevInStack != null, "link corruption", description);
    matches &= check(map.sentinel.nextInStack != null, "link corruption", description);
    matches &= check(map.sentinel.prevInQueue != null, "link corruption", description);
    matches &= check(map.sentinel.nextInQueue != null, "link corruption", description);
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
    Set<Node> seenInStack = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    Set<Node> seenInQueue = newSetFromMap(new IdentityHashMap<Node, Boolean>());

    Node currentInStack = map.sentinel.nextInStack;
    while (currentInStack != map.sentinel) {
      matches &= check(seenInStack.add(currentInStack),
          String.format("Stack loop detected: %s, saw %s in %s",
              currentInStack, seenInStack, map), description);
      matches &= checkDataNode(map, currentInStack, description);
      currentInStack = currentInStack.nextInStack;
    }

    Node currentInQueue = map.sentinel.nextInQueue;
    while (currentInQueue != map.sentinel) {
      matches &= check(seenInQueue.add(currentInQueue),
          String.format("Queue loop detected: %s, saw %s in %s",
              currentInQueue, seenInQueue, map), description);
      matches &= checkDataNode(map, currentInQueue, description);
      currentInQueue = currentInQueue.nextInQueue;
    }

    seen.addAll(seenInStack);
    seen.addAll(seenInQueue);
    for (Node node : seen) {
      weightedSize += node.weightedValue.weight;
    }

    matches &= check(map.size() == seen.size(), "Size != list length", description);
    matches &= check(map.weightedSize() == weightedSize, "WeightedSize != link weights", description);
    return matches;
  }

  /** Validates the sentinel node. */
  private boolean checkSentinel(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    matches &= check(map.sentinel.key == null, "key", description);
    matches &= check(map.sentinel.weightedValue == null, "value", description);
    matches &= check(map.sentinel.segment == -1, "segment", description);
    matches &= check(map.sentinel.prevInStack.nextInStack == map.sentinel, "circular", description);
    matches &= check(map.sentinel.nextInStack.prevInStack == map.sentinel, "circular", description);
    matches &= check(map.sentinel.prevInQueue.nextInQueue == map.sentinel, "circular", description);
    matches &= check(map.sentinel.nextInQueue.prevInQueue == map.sentinel, "circular", description);
    matches &= check(!map.data.containsValue(map.sentinel), "in map", description);
    return matches;
  }

  /** Validates the data node. */
  @SuppressWarnings("unchecked")
  private boolean checkDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
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
    matches &= check(node.isLinked(), "not linked in Lirs", description);

    if (node.inStack()) {
      matches &= check(node.prevInStack != null, "null prevInStack", description);
      matches &= check(node.nextInStack != null, "null nextInStack", description);
      matches &= check(node != node.prevInStack, "circular node", description);
      matches &= check(node != node.nextInStack, "circular node", description);
      matches &= check(node == node.prevInStack.nextInStack, "link corruption", description);
      matches &= check(node == node.nextInStack.prevInStack, "link corruption", description);
    } else {
      matches &= check(node.prevInStack == null, "not null prevInStack", description);
      matches &= check(node.nextInStack == null, "not null nextInStack", description);
    }

    if (node.inQueue()) {
      matches &= check(node.prevInQueue != null, "null prevInQueue", description);
      matches &= check(node.nextInQueue != null, "null nextInQueue", description);
      matches &= check(node != node.prevInQueue, "circular node", description);
      matches &= check(node != node.nextInQueue, "circular node", description);
      matches &= check(node == node.prevInQueue.nextInQueue, "link corruption", description);
      matches &= check(node == node.nextInQueue.prevInQueue, "link corruption", description);
    } else {
      matches &= check(node.prevInQueue == null, "not null prevInQueue", description);
      matches &= check(node.nextInQueue == null, "not null nextInQueue", description);
    }
    matches &= check(node.segment == map.segmentFor(node.key), "bad segment", description);
    return matches;
  }

  /** Validates that the locks are not held. */
  private boolean checkLocks(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    for (Lock lock : Lists.asList(map.evictionLock, map.segmentLock)) {
      boolean isLocked = !((ReentrantLock) lock).isLocked();
      matches &= check(isLocked, "locked", description);
    }
    return matches;
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
