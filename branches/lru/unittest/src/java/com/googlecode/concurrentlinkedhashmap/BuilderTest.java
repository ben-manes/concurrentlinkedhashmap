package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;

import org.testng.annotations.DataProvider;
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
  public void initialCapacity(Builder<?, ?> builder) {
    builder.initialCapacity(100);
    assertEquals(builder.initialCapacity, 100);
    builder.build(); // can't check, so just assert that it builds
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void maximumWeightedCapacity_withNegative() {
    new Builder<Object, Object>().maximumWeightedCapacity(-100);
  }

  @Test(dataProvider = "builder")
  public void maximumWeightedCapacity(Builder<?, ?> builder) {
    ConcurrentLinkedHashMap<?, ?> map = builder.build();
    assertEquals(map.capacity(), capacity());
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void concurrencyLevel_withNegative(Builder<?, ?> builder) {
    builder.concurrencyLevel(-100);
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void concurrencyLevel_withZero(Builder<?, ?> builder) {
    builder.concurrencyLevel(0);
  }

  @Test(dataProvider = "builder")
  public void concurrencyLevel(Builder<?, ?> builder) {
    assertEquals(builder.build().concurrencyLevel, Builder.DEFAULT_CONCURRENCY_LEVEL);
    assertEquals(builder.concurrencyLevel(32).build().concurrencyLevel, 32);
  }

  @Test(dataProvider = "builder", expectedExceptions = NullPointerException.class)
  public void listener_withNull(Builder<?, ?> builder) {
    builder.listener(null);
  }

  @Test(dataProvider = "guardingListener")
  public void listener(EvictionListener<Object, Object> listener) {
    Builder<Object, Object> builder = new Builder<Object, Object>()
        .maximumWeightedCapacity(capacity());
    assertSame(builder.build().listener, DiscardingListener.INSTANCE);
    assertSame(builder.listener(listener).build().listener, listener);
  }

  @Test(dataProvider = "builder", expectedExceptions = NullPointerException.class)
  public void weigher_withNull(Builder<?, ?> builder) {
    builder.weigher(null);
  }

  @Test(dataProvider = "builder")
  public void weigher_withDefault(Builder<Integer, byte[]> builder) {
    assertSame(builder.build().weigher, Weighers.singleton());
  }

  @Test(dataProvider = "builder")
  public void weigher_withCustom(Builder<Integer, byte[]> builder) {
    ConcurrentLinkedHashMap<Integer, byte[]> map = builder
        .weigher(Weighers.byteArray())
        .build();
    assertSame(map.weigher, Weighers.byteArray());
  }

  /** Provides a builder with the capacity set. */
  @DataProvider(name = "builder")
  public Object[][] providesBuilder() {
    return new Object[][] {{
      new Builder<Object, Object>().maximumWeightedCapacity(capacity())
    }};
  }
}
