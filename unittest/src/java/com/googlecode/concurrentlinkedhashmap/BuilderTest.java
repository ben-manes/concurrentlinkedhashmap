package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_CONCURRENCY_LEVEL;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder.DEFAULT_INITIAL_CAPACITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.WeightedCapacityLimiter;

import org.testng.annotations.Test;

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
    assertThat((Object) builder.build().weigher, sameInstance((Object) Weighers.singleton()));
  }

  @Test(dataProvider = "builder")
  public void weigher_withCustom(Builder<Integer, byte[]> builder) {
    builder.weigher(Weighers.byteArray());
    assertThat((Object) builder.build().weigher, is(sameInstance((Object) Weighers.byteArray())));
  }

  @Test(dataProvider = "builder", expectedExceptions = NullPointerException.class)
  public void capacityLimiter_withNull(Builder<?, ?> builder) {
    builder.capacityLimiter(null);
  }

  @Test(dataProvider = "builder")
  public void capacityLimiter_withDefault(Builder<Object, Object> builder) {
    CapacityLimiter capacityLimiter = WeightedCapacityLimiter.INSTANCE;
    assertThat(builder.build().capacityLimiter, is(sameInstance(capacityLimiter)));
  }

  @Test(dataProvider = "builder")
  public void capacityLimiter_withCustom(Builder<Object, Object> builder) {
    CapacityLimiter capacityLimiter = new CapacityLimiter() {
      public boolean hasExceededCapacity(ConcurrentLinkedHashMap<?, ?> map) {
        return false;
      }
    };
    builder.maximumWeightedCapacity(capacity()).capacityLimiter(capacityLimiter);
    assertThat(builder.build().capacityLimiter, is(sameInstance(capacityLimiter)));
  }
}
