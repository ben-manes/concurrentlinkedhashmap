package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.newSetFromMap;

import com.google.common.collect.Lists;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LirsNode;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LirsPolicy;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruNode;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruPolicy;
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
@SuppressWarnings("unchecked")
public final class IsValidState extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("valid");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    drain(map);

    Checker checker = new Checker(description);
    checkMap(map, checker);
//    checkPolicy(map, checker);
//    checkSentinel(map, checker);
    return checker.matches;
  }

  private void drain(ConcurrentLinkedHashMap<?, ?> map) {
    for (;;) {
      map.tryToDrainEvictionQueues(false);

      int pending = 0;
      for (int i = 0; i < map.recencyQueue.length; i++) {
        pending += map.recencyQueueLength.get(i);
      }
      if (pending == 0) {
        break;
      }
    }
  }

  /** Validates the map. */
  private void checkMap(ConcurrentLinkedHashMap<?, ?> map, Checker checker) {
    checker.check(map.writeQueue.isEmpty(), "writeQueue not empty ");
    for (int i = 0; i < map.recencyQueue.length; i++) {
      checker.check(map.recencyQueue[i].isEmpty(), "recencyQueue not empty");
      checker.check(map.recencyQueueLength.get(i) == 0, "recencyQueueLength != 0");
    }
    checker.check(map.listenerQueue.isEmpty(), "listenerQueue");
    checker.check(map.data.size() == map.size(), "Inconsistent size");
    checker.check(map.weightedSize() == map.policy.currentSize, "weightedSize");
    checker.check(map.weightedSize() <= map.capacity(), "overflowed capacity"
        + " [" + map.weightedSize() + " / " + map.capacity() + "]");
    checker.check(map.capacity() == map.policy.capacity, "capacity");
    if (map.isEmpty()) {
      checker.check(new IsEmptyMap().matchesSafely(map, checker.description));
    }
    for (Lock lock : Lists.asList(map.evictionLock, map.segmentLock)) {
      boolean isLocked = !((ReentrantLock) lock).isLocked();
      checker.check(isLocked, "locked");
    }
  }

  /** Validates the sentinel node. */
  private void checkSentinel(ConcurrentLinkedHashMap<?, ?> map, Checker checker) {
    checker.check(map.sentinel.key == null, "key");
    checker.check(map.sentinel.segment == -1, "segment");
    checker.check(map.sentinel.weightedValue == null, "value");
    checker.check(!map.data.containsValue(map.sentinel), "in map");

    if (map.policy instanceof LruPolicy) {
      LruNode sentinel = (LruNode) ((Node) map.sentinel);
      checker.check(sentinel.prev != null, "link corruption");
      checker.check(sentinel.next != null, "link corruption");
      checker.check(sentinel.prev.next == sentinel, "circular");
      checker.check(sentinel.next.prev == sentinel, "circular");
    } else if (map.policy instanceof LirsPolicy) {
      LirsNode sentinel = (LirsNode) ((Node) map.sentinel);
      checker.check(sentinel.prevInStack != null, "link corruption");
      checker.check(sentinel.nextInStack != null, "link corruption");
      checker.check(sentinel.prevInQueue != null, "link corruption");
      checker.check(sentinel.nextInQueue != null, "link corruption");
      checker.check(sentinel.prevInStack.nextInStack == sentinel, "circular");
      checker.check(sentinel.nextInStack.prevInStack == sentinel, "circular");
      checker.check(sentinel.prevInQueue.nextInQueue == sentinel, "circular");
      checker.check(sentinel.nextInQueue.prevInQueue == sentinel, "circular");
    }
  }

  private void checkPolicy(ConcurrentLinkedHashMap<?, ?> map, Checker checker) {
    if (map.policy instanceof LruPolicy) {
      checkLru(map, checker);
    } else if (map.policy instanceof LirsPolicy) {
      checkLirs(map, checker);
    } else {
      checker.check(false, "Unknown page replacement policy");
    }
  }

  /** Validates the LRU algorithm. */
  private void checkLru(ConcurrentLinkedHashMap<?, ?> map, Checker checker) {
    Set<Node> seen = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    LruNode sentinel = (LruNode) ((Node) map.sentinel);
    int weightedSize = 0;

    boolean infinite = false;
    LruNode current = sentinel.next;
    while ((current != sentinel) && !infinite) {
      infinite = seen.add(current);
      checker.check(infinite, "Loop detected: %s, saw %s in %s", current, seen, map);
      checkDataNode(map, current, checker);
      weightedSize += current.weightedValue.weight;
      current = current.next;
    }
    checker.check(map.size() == seen.size(), "Size != list length");
    checker.check(map.weightedSize() == weightedSize, "WeightedSize != link weights");
  }

  /** Validates the LIRS algorithm. */
  private void checkLirs(ConcurrentLinkedHashMap<?, ?> map, Checker checker) {
    LirsNode sentinel = (LirsNode) ((Node) map.sentinel);
    boolean infinite = false;
    int size = 0;

    Set<LirsNode> seen = newSetFromMap(new IdentityHashMap<LirsNode, Boolean>());
    Set<LirsNode> seenInStack = newSetFromMap(new IdentityHashMap<LirsNode, Boolean>());
    Set<LirsNode> seenInQueue = newSetFromMap(new IdentityHashMap<LirsNode, Boolean>());

    LirsNode currentInStack = sentinel.nextInStack;
    while ((currentInStack != map.sentinel) && !infinite) {
      infinite = seenInStack.add(currentInStack);
      checker.check(infinite,
          "Stack loop detected: %s, saw %s in %s", currentInStack, seenInStack, map);
      checkDataNode(map, currentInStack, checker);
      currentInStack = currentInStack.nextInStack;
    }

    LirsNode currentInQueue = sentinel.nextInQueue;
    while ((currentInQueue != sentinel) && !infinite) {
      infinite = seenInQueue.add(currentInQueue);
      checker.check(infinite,
          "Queue loop detected: %s, saw %s in %s", currentInQueue, seenInQueue, map);
      checkDataNode(map, currentInQueue, checker);
      currentInQueue = currentInQueue.nextInQueue;
    }

    seen.addAll(seenInStack);
    seen.addAll(seenInQueue);
    for (Node node : seen) {
      size += node.weightedValue.weight;
    }

    checker.check(map.size() == seen.size(), "Size != list length");
    checker.check(map.weightedSize() == map.size(), "WeightedSize != Size");
    checker.check(map.weightedSize() == size, "WeightedSize != link weights");
  }

  /** Validates the data node. */
  private void checkDataNode(ConcurrentLinkedHashMap<?, ?> map, Node node, Checker checker) {
    boolean matches = true;
    checker.check(node.key != null, "null key");
    checker.check(node.weightedValue != null, "null weighted value");
    checker.check(node.weightedValue.value != null, "null value");
    checker.check(node.weightedValue.weight ==
      ((Weigher) map.weigher).weightOf(node.weightedValue.value), "weight");

    checker.check(map.containsKey(node.key), "inconsistent");
    checker.check(map.containsValue(node.weightedValue.value),
        String.format("Could not find value: %s", node.weightedValue.value));
    checker.check(map.data.get(node.key) == node, "found wrong node");
    checker.check(node.isLinked(), "not linked in Lirs");
    checker.check(node.segment == map.segmentFor(node.key), "bad segment");

    if (map.policy instanceof LruPolicy) {
      checkLruNode((LruNode) node, checker);
    } else if (map.policy instanceof LirsPolicy) {
      checkLirsNode((LirsNode) node, checker);
    }
  }

  /** Validates the LRU node. */
  void checkLruNode(LruNode node, Checker checker) {
    checker.check(node.prev != null, "null prev");
    checker.check(node.next != null, "null next");
    checker.check(node != node.prev, "circular node");
    checker.check(node != node.next, "circular node");
    checker.check(node == node.prev.next, "link corruption");
    checker.check(node == node.next.prev, "link corruption");
  }

  /** Validates the LIRS node. */
  void checkLirsNode(LirsNode node, Checker checker) {
    if (node.inStack()) {
      checker.check(node.prevInStack != null, "null prevInStack");
      checker.check(node.nextInStack != null, "null nextInStack");
      checker.check(node != node.prevInStack, "circular node");
      checker.check(node != node.nextInStack, "circular node");
      checker.check(node == node.prevInStack.nextInStack, "link corruption");
      checker.check(node == node.nextInStack.prevInStack, "link corruption");
    } else {
      checker.check(node.prevInStack == null, "not null prevInStack");
      checker.check(node.nextInStack == null, "not null nextInStack");
    }

    if (node.inQueue()) {
      checker.check(node.prevInQueue != null, "null prevInQueue");
      checker.check(node.nextInQueue != null, "null nextInQueue");
      checker.check(node != node.prevInQueue, "circular node");
      checker.check(node != node.nextInQueue, "circular node");
      checker.check(node == node.prevInQueue.nextInQueue, "link corruption");
      checker.check(node == node.nextInQueue.prevInQueue, "link corruption");
    } else {
      checker.check(node.prevInQueue == null, "not null prevInQueue");
      checker.check(node.nextInQueue == null, "not null nextInQueue");
    }
  }

  final class Checker {
    final Description description;
    boolean matches;

    Checker(Description description) {
      this.description = description;
      this.matches = true;
    }

    void check(boolean expression, String message, Object... args) {
      if (!expression) {
        String separator = matches ? "" : ", ";
        description.appendText(separator + String.format(message, args));
      }
      check(expression);
    }

    void check(boolean expression) {
      matches &= expression;
    }
  }

  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> valid() {
    return new IsValidState();
  }
}
