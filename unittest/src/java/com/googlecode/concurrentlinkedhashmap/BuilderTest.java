package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;

import org.testng.annotations.Test;

/**
 * A unit-test for the builder methods.
 *
 * @author bmanes@google.com (Ben Manes)
 */
public final class BuilderTest extends BaseTest {

  @Test(groups = "development", expectedExceptions=IllegalStateException.class)
  public void unconfigured() {
    new Builder<Object, Object>().build();
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void initialCapacity_withNegative() {
    builder().initialCapacity(-100);
  }

  @Test(groups = "development")
  public void initialCapacity() {
    Builder<?, ?> builder = builder().initialCapacity(100);
    assertEquals(builder.initialCapacity, 100);
    builder.build(); // can't check, so just assert that it builds
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void maximumWeightedCapacity_withNegative() {
    builder().maximumWeightedCapacity(-100);
  }

  @Test(groups = "development")
  public void maximumWeightedCapacity() {
    ConcurrentLinkedHashMap<?, ?> cache = builder().build();
    assertEquals(cache.capacity(), capacity);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void concurrencyLevel_withNegative() {
    builder().concurrencyLevel(-100);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void concurrencyLevel_withZero() {
    builder().concurrencyLevel(0);
  }

  @Test(groups = "development")
  public void concurrencyLevel() {
    assertEquals(builder().build().concurrencyLevel, Builder.DEFAULT_CONCURRENCY_LEVEL);
    assertEquals(builder().concurrencyLevel(32).build().concurrencyLevel, 32);
  }

  @Test(groups = "development", expectedExceptions=NullPointerException.class)
  public void listener_withNull() {
    builder().listener(null);
  }

  @Test(groups = "development")
  public void listener() {
    EvictionListener<Object, Object> listener = EvictionMonitor.newGuard();
    assertSame(builder().build().listener, DiscardingListener.INSTANCE);
    assertSame(builder().listener(listener).build().listener, listener);
  }

  @Test(groups = "development", expectedExceptions=NullPointerException.class)
  public void weigher_withNull() {
    builder().weigher(null);
  }

  @Test(groups = "development")
  public void weigher() {
    ConcurrentLinkedHashMap<Integer, byte[]> map = super.<Integer, byte[]>builder()
        .weigher(Weighers.byteArray())
        .build();
    assertSame(builder().build().weigher, Weighers.singleton());
    assertSame(map.weigher, Weighers.byteArray());
  }
}
