package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Maps.immutableEntry;
import static com.googlecode.concurrentlinkedhashmap.ValidState.valid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Base utilities for testing purposes.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public abstract class BaseTest {
  private boolean debug;

  /** Retrieves the maximum weighted capacity to build maps with. */
  protected abstract int capacity();

  /** Initializes the test with runtime properties. */
  @BeforeClass(alwaysRun = true)
  public void before() {
    debug = booleanProperty("test.debugMode");
    info("\nRunning %s...\n", getClass().getSimpleName());
  }

  protected static int intProperty(String property) {
    return Integer.valueOf(System.getProperty(property));
  }

  protected static boolean booleanProperty(String property) {
    return Boolean.valueOf(System.getProperty(property));
  }

  protected static <E extends Enum<E>> E enumProperty(String property, Class<E> clazz) {
    return Enum.valueOf(clazz, System.getProperty(property).toUpperCase());
  }

  /* ---------------- Logging methods -------------- */

  protected void info(String message, Object... args) {
    System.out.printf(message + "\n", args);
  }

  protected void debug(String message, Object... args) {
    if (debug) {
      info(message, args);
    }
  }

  /* ---------------- Testing aspects -------------- */

  private ConcurrentLinkedHashMap<?, ?> rawMap;

  /** Validates the state of a provided map. */
  @AfterMethod(alwaysRun = true)
  public void verifyValidState(ITestResult result) {
    boolean successful = result.isSuccess();
    try {
      if (rawMap != null) { // dataProvider used
        assertThat(rawMap, is(valid()));
      }
    } catch (Throwable caught) {
      successful = false;
      fail("Test: " + result.getMethod().getMethodName(), caught);
    } finally {
      if (!successful) {
        info(" * %s: Failed", result.getMethod().getMethodName());
      }
    }
  }

  /* ---------------- Map providers -------------- */

  /** Provides a builder with the capacity set. */
  @DataProvider(name = "builder")
  public Object[][] providesBuilder() {
    return new Object[][] {{
      new Builder<Object, Object>().maximumWeightedCapacity(capacity())
    }};
  }

  /** Provides an empty map for test methods. */
  @DataProvider(name = "emptyMap")
  public Object[][] providesEmptyMap() {
    rawMap = newEmptyMap();
    return new Object[][] {{ rawMap }};
  }

  /** Creates a map with the default capacity. */
  protected <K, V> ConcurrentLinkedHashMap<K, V> newEmptyMap() {
    return new Builder<K, V>()
        .maximumWeightedCapacity(capacity())
        .build();
  }

  /** Provides a guarded map for test methods. */
  @DataProvider(name = "guardedMap")
  public Object[][] providesGuardedMap() {
    rawMap = newGuarded();
    return new Object[][] {{ rawMap }};
  }

  /** Creates a map that fails if an eviction occurs. */
  protected <K, V> ConcurrentLinkedHashMap<K, V> newGuarded() {
    return new Builder<K, V>()
        .listener(new GuardingListener<K, V>())
        .maximumWeightedCapacity(capacity())
        .build();
  }

  /** Provides a warmed map for test methods. */
  @DataProvider(name = "warmedMap")
  public Object[][] providesWarmedMap() {
    rawMap = newWarmedMap();
    return new Object[][] {{ rawMap }};
  }

  /** Creates a map with warmed to capacity. */
  protected ConcurrentLinkedHashMap<Integer, Integer> newWarmedMap() {
    ConcurrentLinkedHashMap<Integer, Integer> map = newEmptyMap();
    warmUp(map, 0, capacity());
    return map;
  }

  /**
   * Populates the map with the half-closed interval [start, end) where the
   * value is the negation of the key.
   */
  protected void warmUp(Map<Integer, Integer> map, int start, int end) {
    for (Integer i = start; i < end; i++) {
      assertThat(map.put(i, -i), is(nullValue()));
    }
  }

  /* ---------------- Weigher providers -------------- */

  @DataProvider(name = "singletonWeigher")
  public Object[][] providesSingletonWeigher() {
    return new Object[][] {{ Weighers.singleton() }};
  }

  @DataProvider(name = "byteArrayWeigher")
  public Object[][] providesByteArrayWeigher() {
    return new Object[][] {{ Weighers.byteArray() }};
  }

  @DataProvider(name = "iterableWeigher")
  public Object[][] providesIterableWeigher() {
    return new Object[][] {{ Weighers.iterable() }};
  }

  @DataProvider(name = "collectionWeigher")
  public Object[][] providesCollectionWeigher() {
    return new Object[][] {{ Weighers.collection() }};
  }

  @DataProvider(name = "listWeigher")
  public Object[][] providesListWeigher() {
    return new Object[][] {{ Weighers.list() }};
  }

  @DataProvider(name = "setWeigher")
  public Object[][] providesSetWeigher() {
    return new Object[][] {{ Weighers.set() }};
  }

  @DataProvider(name = "mapWeigher")
  public Object[][] providesMapWeigher() {
    return new Object[][] {{ Weighers.map() }};
  }

  /* ---------------- Listener providers -------------- */

  private EvictionListener<?, ?> rawListener;

  /** Provides a listener that fails on eviction. */
  @DataProvider(name = "guardingListener")
  public Object[][] providesGuardedListener() {
    rawListener = new GuardingListener<Object, Object>();
    return new Object[][] {{ rawListener }};
  }

  /** Provides a listener that collects evicted entries. */
  @DataProvider(name = "collectingListener")
  public Object[][] providesMonitorableListener() {
    rawListener = new CollectingListener<Object, Object>();
    return new Object[][] {{ rawListener }};
  }

  /** A listener that fails if invoked. */
  private static final class GuardingListener<K, V>
    implements EvictionListener<K, V>, Serializable {

    @Override public void onEviction(Object key, Object value) {
      fail(String.format("Evicted %s=%s", key, value));
    }

    private static final long serialVersionUID = 1L;
  }

  /** A listener that either collects the evicted entries. */
  protected static final class CollectingListener<K, V>
      implements EvictionListener<K, V>, Serializable {
    final Collection<Entry<K, V>> evicted;

    private CollectingListener() {
      this.evicted = new ConcurrentLinkedQueue<Entry<K, V>>();
    }

    @Override public void onEviction(K key, V value) {
      evicted.add(immutableEntry(key, value));
    }

    private static final long serialVersionUID = 1L;
  }
}
