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

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_CONCURRENCY_LEVEL;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_INITIAL_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_EXEUCTOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.BoundedWeigher;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;

import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A unit-test for the builder methods.
 *
 * @author bmanes@google.com (Ben Manes)
 */
@Test(groups = "development")
public final class BuilderTest extends BaseTest {

  @Override
  protected int capacity() {
    return 100;
  }

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
    assertThat(builder.initialCapacity, is(equalTo(DEFAULT_INITIAL_CAPACITY)));
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
    assertThat(builder.build().capacity(), is(equalTo(MAXIMUM_CAPACITY)));
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
    assertThat(builder.build().concurrencyLevel, is(equalTo(DEFAULT_CONCURRENCY_LEVEL)));
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

  @Test(dataProvider = "guardingListener")
  public void listener_withCustom(EvictionListener<Object, Object> listener) {
    Builder<Object, Object> builder = new Builder<Object, Object>()
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
    Weigher<?> weigher = ((BoundedWeigher<?>) builder.build().weigher).delegate;
    assertThat(weigher, sameInstance((Object) Weighers.singleton()));
  }

  @Test(dataProvider = "builder")
  public void weigher_withCustom(Builder<Integer, byte[]> builder) {
    builder.weigher(Weighers.byteArray());
    Weigher<?> weigher = ((BoundedWeigher<?>) builder.build().weigher).delegate;
    assertThat(weigher, is(sameInstance((Object) Weighers.byteArray())));
  }

  @Test(dataProvider = "builder")
  public void catchup_withDefault(Builder<?, ?> builder) {
    assertThat(builder.build().executor, is(DEFAULT_EXEUCTOR));
  }

  @Test(dataProvider = "builder")
  public void catchup_withCustom(Builder<?, ?> builder) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setDaemon(true).build());
    builder.catchup(executor, 1, TimeUnit.MINUTES).build();

    assertThat(builder.executor, is((ExecutorService) executor));
    assertThat(executor.shutdownNow(), hasSize(1));
  }

  @Test(dataProvider = "builder", expectedExceptions = RejectedExecutionException.class)
  public void catchup_withRejected(Builder<?, ?> builder) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setDaemon(true).build());
    builder.catchup(executor, 1, TimeUnit.MINUTES);
    executor.shutdownNow();
    builder.build();
  }
}
