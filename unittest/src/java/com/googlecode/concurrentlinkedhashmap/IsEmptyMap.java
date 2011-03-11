/*
 * Copyright 2011 Benjamin Manes
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

import com.google.common.collect.ImmutableMap;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Map;

/**
 * A matcher that performs an exhaustive empty check throughout the {@link Map}
 * and {@link ConcurrentLinkedHashMap} contract.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEmptyMap extends TypeSafeDiagnosingMatcher<Map<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("empty");
  }

  @Override
  protected boolean matchesSafely(Map<?, ?> map, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    builder.expect(new IsEmptyCollection().matchesSafely(map.keySet(), description));
    builder.expect(new IsEmptyCollection().matchesSafely(map.values(), description));
    builder.expect(new IsEmptyCollection().matchesSafely(map.entrySet(), description));
    builder.expect(map.isEmpty(), "Not empty");
    builder.expectEqual(map, ImmutableMap.of(), "Not equal to empty map");
    builder.expectEqual(map.hashCode(), ImmutableMap.of().hashCode(), "hashcode");
    builder.expectEqual(map.toString(), ImmutableMap.of().toString(), "toString");
    if (map instanceof ConcurrentLinkedHashMap<?, ?>) {
      checkIsEmpty((ConcurrentLinkedHashMap<?, ?>) map, builder);
    }
    return builder.matches();
  }

  private void checkIsEmpty(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    map.tryToDrainBuffers();

    builder.expectEqual(map.size(), 0, "Size != 0");
    builder.expect(map.data.isEmpty(), "Internal not empty");
    builder.expectEqual(map.data.size(), 0, "Internal size != 0");
    builder.expectEqual(map.weightedSize(), 0, "Weighted size != 0");
    builder.expectEqual(map.weightedSize, 0, "Internal weighted size != 0");
    builder.expectEqual(map.evictionDeque.peekFirst(), null, "first not null: " + map.evictionDeque);
    builder.expectEqual(map.evictionDeque.peekLast(), null, "last not null");
  }

  @Factory
  public static IsEmptyMap emptyMap() {
    return new IsEmptyMap();
  }
}
