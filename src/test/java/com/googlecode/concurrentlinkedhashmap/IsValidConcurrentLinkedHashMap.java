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
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruPolicy;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Task;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.WeightedValue;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.AMORTIZED_DRAIN_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyMap.emptyMap;
import static com.googlecode.concurrentlinkedhashmap.IsValidLinkedDeque.validLinkedDeque;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * A matcher that evaluates a {@link ConcurrentLinkedHashMap} to determine if it
 * is in a valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class IsValidConcurrentLinkedHashMap<K, V>
    extends TypeSafeDiagnosingMatcher<ConcurrentLinkedHashMap<? extends K, ? extends V>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("valid");
  }

  @Override
  protected boolean matchesSafely(ConcurrentLinkedHashMap<? extends K, ? extends V> map,
      Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    drain(map);
    checkMap(map, builder);
    checkPolicy(map, builder);
    return builder.matches();
  }

  private void drain(ConcurrentLinkedHashMap<? extends K, ? extends V> map) {
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

  private void checkMap(ConcurrentLinkedHashMap<? extends K, ? extends V> map,
      DescriptionBuilder builder) {
    for (int i = 0; i < map.buffers.length; i++) {
      builder.expectThat("recencyQueue not empty", map.buffers[i].isEmpty(), is(true));
      builder.expectThat("recencyQueueLength != 0", map.bufferLengths.get(i), is(0));
    }
    builder.expectThat("listenerQueue", map.pendingNotifications.isEmpty(), is(true));
    builder.expectThat("Inconsistent size", map.data.size(), is(map.size()));
    builder.expectThat("weightedSize", map.weightedSize(), is(map.weightedSize.get()));
    builder.expectThat("capacity", map.capacity(), is(map.capacity));
    builder.expectThat("overflow", map.capacity, is(greaterThanOrEqualTo(map.weightedSize())));
    builder.expectThat(((ReentrantLock) map.evictionLock).isLocked(), is(false));

    if (map.isEmpty()) {
      builder.expectThat(map, emptyMap());
    }
  }

  private void checkPolicy(ConcurrentLinkedHashMap<? extends K, ? extends V> map,
      DescriptionBuilder builder) {
    if (map.policy instanceof LruPolicy) {
      checkLruPolicy(map, builder);
    } else {
      checkLirsPolicy(map, builder);
    }
  }

  private void checkLruPolicy(ConcurrentLinkedHashMap<? extends K, ? extends V> map,
      DescriptionBuilder builder) {
    LruPolicy<?, ?> policy = (LruPolicy<?, ?>) map.policy;
    AbstractLinkedDeque<?> deque = policy.evictionQueue;

    checkLinks(map, policy, builder);
    builder.expectThat(deque, hasSize(map.size()));
    validLinkedDeque().matchesSafely(deque, builder.getDescription());
  }

  @SuppressWarnings("rawtypes")
  private void checkLinks(ConcurrentLinkedHashMap<? extends K, ? extends V> map,
      LruPolicy<?, ?> policy, DescriptionBuilder builder) {
    long weightedSize = 0;
    Set<Node> seen = Sets.newIdentityHashSet();
    for (Node node : policy.evictionQueue) {
      String errorMsg = String.format("Loop detected: %s, saw %s in %s", node, seen, map);
      builder.expectThat(errorMsg, seen.add(node), is(true));
      weightedSize += ((WeightedValue) node.get()).weight;
      checkNode(map, node, builder);
    }

    builder.expectThat("Size != list length", map.size(), is(seen.size()));
    builder.expectThat("WeightedSize != link weights"
        + " [" + map.weightedSize() + " vs. " + weightedSize + "]"
        + " {size: " + map.size() + " vs. " + seen.size() + "}",
        map.weightedSize(), is(weightedSize));
  }

  @SuppressWarnings("rawtypes")
  private void checkNode(ConcurrentLinkedHashMap<? extends K, ? extends V> map, Node node,
      DescriptionBuilder builder) {
    builder.expectThat(node.key, is(not(nullValue())));
    builder.expectThat(node.get(), is(not(nullValue())));
    builder.expectThat(node.getValue(), is(not(nullValue())));
    builder.expectThat("weight", ((WeightedValue) node.get()).weight,
        is(((EntryWeigher) map.weigher).weightOf(node.key, node.getValue())));

    builder.expectThat("inconsistent", map, hasKey(node.key));
    builder.expectThat("Could not find value: " + node.getValue(), map, hasValue(node.getValue()));
    builder.expectThat("found wrong node", map.data, hasEntry(node.key, node));
  }

  private void checkLirsPolicy(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    // TBD
  }

  @Factory
  public static <K, V> IsValidConcurrentLinkedHashMap<K, V> valid() {
    return new IsValidConcurrentLinkedHashMap<K, V>();
  }
}
