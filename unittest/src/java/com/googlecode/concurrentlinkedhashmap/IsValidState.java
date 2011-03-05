package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.newSetFromMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.EvictionDeque;
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
@SuppressWarnings("unchecked")
public final class IsValidState extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("state");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    drain(map);
    checkMap(map, builder);
    checkEvictionDeque(map, builder);
    return builder.matches();
  }

  private void drain(ConcurrentLinkedHashMap<?, ?> map) {
    for (;;) {
      map.tryToDrainEvictionQueues(false);

      int pending = 0;
      for (int i = 0; i < map.bufferLength.length(); i++) {
        pending += map.bufferLength.get(i);
      }
      if (pending == 0) {
        break;
      }
    }
  }

  private void checkMap(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    for (int i = 0; i < map.buffers.length; i++) {
      builder.expect(map.buffers[i].isEmpty(), "recencyQueue not empty");
      builder.expect(map.bufferLength.get(i) == 0, "recencyQueueLength != 0");
    }
    builder.expect(map.listenerQueue.isEmpty(), "listenerQueue");
    builder.expectEqual(map.data.size(), map.size(), "Inconsistent size");
    builder.expectEqual(map.weightedSize(), map.weightedSize, "weightedSize");
    builder.expectEqual(map.capacity(), map.capacity, "capacity");
    builder.expect(map.capacity >= map.weightedSize(), "overflow");
    builder.expectNot(((ReentrantLock) map.evictionLock).isLocked());

    boolean empty = new IsEmptyMap().matchesSafely(map, builder.getDescription());
    if (map.isEmpty()) {
      builder.expect(empty);
    } else {
      builder.expectNot(empty);
    }
  }

  private void checkEvictionDeque(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    EvictionDeque deque = map.evictionDeque;

    checkLinks(map, builder);
    builder.expectEqual(deque.size(), map.size());
  }

  /** Validates the links. */
  private void checkLinks(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    checkSentinel(map, builder);

    int weightedSize = 0;
    Set<Node> seen = newSetFromMap(new IdentityHashMap<Node, Boolean>());
    for (Node node : map.evictionDeque) {
      builder.expect(seen.add(node), "Loop detected: %s, saw %s in %s", node, seen, map);
      checkNode(map, node, builder);
      weightedSize += node.getWeightedValue().weight;
    }
    builder.expectEqual(map.size(), seen.size(), "Size != list length");
    builder.expectEqual(map.weightedSize(), weightedSize, "WeightedSize != link weights"
        + " [" + map.weightedSize() + " vs. " + weightedSize + "]"
        + " {size: " + map.size() + " vs. " + seen.size() + "}");
  }

  /** Validates the sentinel node. */
  private void checkSentinel(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    Node sentinel = map.evictionDeque.sentinel;

    builder.expectNotEqual(sentinel.prev, null, "link corruption");
    builder.expectNotEqual(sentinel.next, null, "link corruption");

    builder.expectEqual(sentinel.key, null, "key");
    builder.expectEqual(sentinel.getWeightedValue(), null, "value");
    builder.expectNot(map.data.containsValue(sentinel), "in map");

    builder.expectNotEqual(sentinel.prev, null, "link corruption");
    builder.expectNotEqual(sentinel.next, null, "link corruption");
    builder.expectEqual(sentinel.prev.next, sentinel, "circular");
    builder.expectEqual(sentinel.next.prev, sentinel, "circular");
  }

  /** Validates the data node. */
  private void checkNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
      DescriptionBuilder builder) {
    builder.expectNotEqual(node.key, null, "null key");
    builder.expectNotEqual(node.getWeightedValue(), null, "null weighted value");
    builder.expectNotEqual(node.getWeightedValue().value, null, "null value");
    builder.expectEqual(node.getWeightedValue().weight,
      ((Weigher) map.weigher).weightOf(node.getWeightedValue().value), "weight");

    builder.expect(map.containsKey(node.key), "inconsistent");
    builder.expect(map.containsValue(node.getWeightedValue().value),
        "Could not find value: %s", node.getWeightedValue().value);
    builder.expectEqual(map.data.get(node.key), node, "found wrong node");
    builder.expectNotEqual(node.prev, null, "null prev");
    builder.expectNotEqual(node.next, null, "null next");
    builder.expectNotEqual(node, node.prev, "circular node");
    builder.expectNotEqual(node, node.next, "circular node");
    builder.expectEqual(node, node.prev.next, "link corruption");
    builder.expectEqual(node, node.next.prev, "link corruption");
  }

  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> valid() {
    return new IsValidState();
  }
}
