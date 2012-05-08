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

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_CONCURRENCY_LEVEL;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_INITIAL_CAPACITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import org.testng.annotations.Test;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.BoundedWeigher;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;

/**
 * A unit-test for the builder methods.
 *
 * @author bmanes@google.com (Ben Manes)
 */
@Test(groups = "development")
public final class BuilderTest extends AbstractTest {

  @Test(expectedExceptions = IllegalStateException.class)
  public void unconfigured() {
    new Builder<Object, Object>().build();
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void initialCapacity_withNegative(Builder<?, ?> builder) {
    builder.initialCapacity(-100);
  }

  @Test(dataProvider = "builder")
  public void initialCapacity_withDefault(Builder<?, ?> builder) {
    assertThat(builder.initialCapacity, is(DEFAULT_INITIAL_CAPACITY));
    builder.build(); // can't check, so just assert that it builds
  }

  @Test(dataProvider = "builder")
  public void initialCapacity_withCustom(Builder<?, ?> builder) {
    assertThat(builder.initialCapacity(100).initialCapacity, is(equalTo(100)));
    builder.build(); // can't check, so just assert that it builds
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void maximumWeightedCapacity_withNegative(Builder<?, ?> builder) {
    builder.maximumWeightedCapacity(-100);
  }

  @Test(dataProvider = "builder")
  public void maximumWeightedCapacity(Builder<?, ?> builder) {
    assertThat(builder.build().capacity(), is(equalTo(capacity())));
  }

  @Test(dataProvider = "builder")
  public void maximumWeightedCapacity_aboveMaximum(Builder<?, ?> builder) {
    builder.maximumWeightedCapacity(MAXIMUM_CAPACITY + 1);
    assertThat(builder.build().capacity(), is(MAXIMUM_CAPACITY));
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void concurrencyLevel_withZero(Builder<?, ?> builder) {
    builder.concurrencyLevel(0);
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void concurrencyLevel_withNegative(Builder<?, ?> builder) {
    builder.concurrencyLevel(-100);
  }

  @Test(dataProvider = "builder")
  public void concurrencyLevel_withDefault(Builder<?, ?> builder) {
    assertThat(builder.build().concurrencyLevel, is(DEFAULT_CONCURRENCY_LEVEL));
  }

  @Test(dataProvider = "builder")
  public void concurrencyLevel_withCustom(Builder<?, ?> builder) {
    assertThat(builder.concurrencyLevel(32).build().concurrencyLevel, is(32));
  }

  @Test(dataProvider = "builder", expectedExceptions = NullPointerException.class)
  public void listener_withNull(Builder<?, ?> builder) {
    builder.listener(null);
  }

  @Test(dataProvider = "builder")
  public void listener_withDefault(Builder<Object, Object> builder) {
    EvictionListener<Object, Object> listener = DiscardingListener.INSTANCE;
    assertThat(builder.build().listener, is(sameInstance(listener)));
  }

  @Test
  public void listener_withCustom() {
    Builder<Integer, Integer> builder = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .listener(listener);
    assertThat(builder.build().listener, is(sameInstance(listener)));
  }

  @Test(dataProvider = "builder", expectedExceptions = NullPointerException.class)
  public void weigher_withNull(Builder<?, ?> builder) {
    builder.weigher(null);
  }

  @Test(dataProvider = "builder")
  public void weigher_withDefault(Builder<Integer, Integer> builder) {
    assertThat(builder.build().weigher, sameInstance((Object) Weighers.singleton()));
  }

  @Test(dataProvider = "builder")
  public void weigher_withCustom(Builder<Integer, byte[]> builder) {
    builder.weigher(Weighers.byteArray());
    Weigher<?> weigher = ((BoundedWeigher<?>) builder.build().weigher).weigher;
    assertThat(weigher, is(sameInstance((Object) Weighers.byteArray())));
  }
}
