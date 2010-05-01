package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.DiscardingListener;

import org.testng.annotations.Test;

/**
 * A unit-test for the builder methods.
 *
 * @author bmanes@google.com (Ben Manes)
 */
public final class BuilderTest extends BaseTest {

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void unconfigured() {
    debug(" * unconfigured: START");
    new Builder().build();
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void negativeInitialCapacity() {
    debug(" * negativeInitialCapacity: START");
    builder().initialCapacity(-100);
  }

  @Test(groups = "development")
  public void initialCapacity() {
    debug(" * initialCapacity: START");
    Builder<?, ?> builder = builder().initialCapacity(100);
    assertEquals(builder.initialCapacity, 100);
    builder.build(); // can't check, so just assert that it builds
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void negativeMaximumWeightedCapacity() {
    debug(" * negativeMaximumWeightedCapacity: START");
    builder().maximumWeightedCapacity(-100);
  }

  @Test(groups = "development")
  public void maximumWeightedCapacity() {
    debug(" * maximumWeightedCapacity: START");
    ConcurrentLinkedHashMap<?, ?> cache = builder().build();
    assertEquals(cache.capacity(), capacity);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void negativeConcurrencyLevel() {
    debug(" * negativeConcurrencyLevel: START");
    builder().concurrencyLevel(-100);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void zeroConcurrencyLevel() {
    debug(" * zeroConcurrencyLevel: START");
    builder().concurrencyLevel(0);
  }

  @Test(groups = "development")
  public void concurrencyLevel() {
    debug(" * concurrencyLevel: START");
    assertEquals(builder().build().concurrencyLevel, Builder.DEFAULT_CONCURRENCY_LEVEL);
    assertEquals(builder().concurrencyLevel(32).build().concurrencyLevel, 32);
  }

  @Test(groups = "development", expectedExceptions=NullPointerException.class)
  public void nullListener() {
    debug(" * nullListener: START");
    builder().listener(null);
  }

  @Test(groups = "development")
  public void listener() {
    debug(" * listener: START");
    EvictionListener<Object, Object> listener = EvictionMonitor.newGuard();
    assertSame(builder().build().listener, DiscardingListener.INSTANCE);
    assertSame(builder().listener(listener).build().listener, listener);
  }

  @Test(groups = "development", expectedExceptions=NullPointerException.class)
  public void nullWeigher() {
    debug(" * nullWeigher: START");
    builder().weigher(null);
  }

  @Test(groups = "development")
  public void weigher() {
    debug(" * weigher: START");
    assertSame(builder().build().weigher, Weighers.singleton());
    assertSame(super.<Integer, byte[]>builder().weigher(Weighers.byteArray()).build().weigher,
        Weighers.byteArray());
  }
}
