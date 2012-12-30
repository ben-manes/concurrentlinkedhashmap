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

import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static com.googlecode.concurrentlinkedhashmap.IsEmptyCollection.emptyCollection;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * A matcher that performs an exhaustive empty check throughout the {@link Map}
 * and {@link ConcurrentLinkedHashMap} contract.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEmptyMap<K, V>
    extends TypeSafeDiagnosingMatcher<Map<? extends K, ? extends V>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("empty");
  }

  @Override
  protected boolean matchesSafely(Map<? extends K, ? extends V> map, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    builder.expectThat(map.keySet(), is(emptyCollection()));
    builder.expectThat(map.values(), is(emptyCollection()));
    builder.expectThat(map.entrySet(), is(emptyCollection()));
    builder.expectThat(map, is(Collections.EMPTY_MAP));
    builder.expectThat("Size != 0", map.size(), is(0));
    builder.expectThat("Not empty", map.isEmpty(), is(true));
    builder.expectThat("hashcode", map.hashCode(), is(ImmutableMap.of().hashCode()));
    builder.expectThat("toString", map, hasToString(ImmutableMap.of().toString()));
    if (map instanceof ConcurrentLinkedHashMap<?, ?>) {
      checkIsEmpty((ConcurrentLinkedHashMap<?, ?>) map, builder);
    }
    return builder.matches();
  }

  private void checkIsEmpty(ConcurrentLinkedHashMap<?, ?> map, DescriptionBuilder builder) {
    map.drainBuffers();

    builder.expectThat("Internal not empty", map.data.isEmpty(), is(true));
    builder.expectThat("Internal size != 0", map.data.size(), is(0));
    builder.expectThat("Weighted size != 0", map.weightedSize(), is(0L));
    builder.expectThat("Internal weighted size != 0", map.weightedSize.get(), is(0L));
    builder.expectThat("first not null: " + map.policy.residentQueue,
        map.policy.residentQueue.peekFirst(), is(nullValue()));
    builder.expectThat("last not null", map.policy.residentQueue.peekLast(), is(nullValue()));
  }

  @Factory
  public static <K, V> IsEmptyMap<K, V> emptyMap() {
    return new IsEmptyMap<K, V>();
  }
}
