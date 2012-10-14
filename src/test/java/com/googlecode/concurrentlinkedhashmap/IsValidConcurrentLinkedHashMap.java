/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.Sets;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Task;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.WeightedValue;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.AMORTIZED_DRAIN_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.IsValidLinkedDeque.validLinkedDeque;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * A matcher that evaluates a {@link ConcurrentLinkedHashMap} to determine if it
 * is in a valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class IsValidConcurrentLinkedHashMap
    extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("valid");
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
      map.drainBuffers();
      assertThat(map.tasks, is(equalTo(new Task[AMORTIZED_DRAIN_THRESHOLD])));

      int pending = 0;
      for (int i = 0; i < map.bufferLengths.length(); i++) {
        pending += map.bufferLengths.get(i);
      }
      if (pending == 0) {
        break;
      }
    }
  }

  private void checkMap(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    for (int i = 0; i < map.buffers.length; i++) {
      builder.expect(map.buffers[i].isEmpty(), "recencyQueue not empty");
      builder.expect(map.bufferLengths.get(i) == 0, "recencyQueueLength != 0");
    }
    builder.expect(map.pendingNotifications.isEmpty(), "listenerQueue");
    builder.expectEqual(map.data.size(), map.size(), "Inconsistent size");
    builder.expectEqual(map.weightedSize(), map.weightedSize.get(), "weightedSize");
    builder.expectEqual(map.capacity(), map.capacity, "capacity");
    builder.expect(map.capacity >= map.weightedSize(), "overflow");
    builder.expectNot(((ReentrantLock) map.evictionLock).isLocked());

    if (map.isEmpty()) {
      builder.expect(IsEmptyMap.emptyMap().matchesSafely(map, builder.getDescription()));
    }
  }

  private void checkEvictionDeque(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    LinkedDeque<?> deque = map.evictionDeque;

    checkLinks(map, builder);
    builder.expectEqual(deque.size(), map.size());
    validLinkedDeque().matchesSafely(map.evictionDeque, builder.getDescription());
  }

  @SuppressWarnings("rawtypes")
  private void checkLinks(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    long weightedSize = 0;
    Set<Node> seen = Sets.newIdentityHashSet();
    for (Node node : map.evictionDeque) {
      builder.expect(seen.add(node), "Loop detected: %s, saw %s in %s", node, seen, map);
      weightedSize += ((WeightedValue) node.get()).weight;
      checkNode(map, node, builder);
    }

    builder.expectEqual(map.size(), seen.size(), "Size != list length");
    builder.expectEqual(map.weightedSize(), weightedSize, "WeightedSize != link weights"
        + " [" + map.weightedSize() + " vs. " + weightedSize + "]"
        + " {size: " + map.size() + " vs. " + seen.size() + "}");
  }

  @SuppressWarnings("rawtypes")
  private void checkNode(ConcurrentLinkedHashMap<?, ?> map, Node node,
      DescriptionBuilder builder) {
    builder.expectNotEqual(node.key, null, "null key");
    builder.expectNotEqual(node.get(), null, "null weighted value");
    builder.expectNotEqual(node.getValue(), null, "null value");
    builder.expectEqual(((WeightedValue) node.get()).weight,
        ((EntryWeigher) map.weigher).weightOf(node.key, node.getValue()), "weight");

    builder.expect(map.containsKey(node.key), "inconsistent");
    builder.expect(map.containsValue(node.getValue()),
        "Could not find value: %s", node.getValue());
    builder.expectEqual(map.data.get(node.key), node, "found wrong node");
  }

  @Factory
  public static IsValidConcurrentLinkedHashMap valid() {
    return new IsValidConcurrentLinkedHashMap();
  }
}
