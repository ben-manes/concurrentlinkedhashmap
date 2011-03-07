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

import static com.google.common.collect.Maps.immutableEntry;
import static com.googlecode.concurrentlinkedhashmap.IsValidState.valid;
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

  /* ---------------- Properties methods ----------- */

  protected static int intProperty(String property) {
    return Integer.getInteger(property);
  }

  protected static boolean booleanProperty(String property) {
    return Boolean.getBoolean(property);
  }

  protected static <E extends Enum<E>> E enumProperty(String property, Class<E> clazz) {
    return Enum.valueOf(clazz, System.getProperty(property));
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

  @BeforeClass(alwaysRun = true)
  public void before() {
    debug = booleanProperty("test.debugMode");
    info("\nRunning %s...\n", getClass().getSimpleName());
  }

  @AfterMethod(alwaysRun = true)
  public void after(ITestResult result) {
    try {
      if (result.isSuccess()) {
        for (Object provided : result.getParameters()) {
          validate(provided);
        }
      }
    } catch (AssertionError caught) {
      result.setStatus(ITestResult.FAILURE);
      result.setThrowable(caught);
    } finally {
      if (!result.isSuccess()) {
        info(" * %s: Failed", result.getMethod().getMethodName());
      }
    }
  }

  /** Validates the state of the injected parameter. */
  private static void validate(Object param) {
    if (param instanceof ConcurrentLinkedHashMap<?, ?>) {
      assertThat((ConcurrentLinkedHashMap<?, ?>) param, is(valid()));
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
    return new Object[][] {{ newEmptyMap() }};
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
    return new Object[][] {{ newGuarded() }};
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
    return new Object[][] {{ newWarmedMap() }};
  }

  /** Creates a map with warmed to capacity. */
  protected ConcurrentLinkedHashMap<Integer, Integer> newWarmedMap() {
    ConcurrentLinkedHashMap<Integer, Integer> map = newEmptyMap();
    warmUp(map, 0, capacity());
    return map;
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

  /** Provides a listener that fails on eviction. */
  @DataProvider(name = "guardingListener")
  public Object[][] providesGuardedListener() {
    return new Object[][] {{ new GuardingListener<Object, Object>() }};
  }

  /** Provides a listener that collects evicted entries. */
  @DataProvider(name = "collectingListener")
  public Object[][] providesMonitorableListener() {
    return new Object[][] {{ new CollectingListener<Object, Object>() }};
  }

  /** A listener that fails if invoked. */
  private static final class GuardingListener<K, V>
    implements EvictionListener<K, V>, Serializable {

    @Override public void onEviction(Object key, Object value) {
      fail(String.format("Evicted %s=%s", key, value));
    }

    private static final long serialVersionUID = 1L;
  }

  /** A listener that collects the evicted entries. */
  protected static final class CollectingListener<K, V>
      implements EvictionListener<K, V>, Serializable {
    final Collection<Entry<K, V>> evicted;

    public CollectingListener() {
      this.evicted = new ConcurrentLinkedQueue<Entry<K, V>>();
    }

    @Override public void onEviction(K key, V value) {
      evicted.add(immutableEntry(key, value));
    }

    private static final long serialVersionUID = 1L;
  }

  /* ---------------- Utility methods ------------- */

  /**
   * Populates the map with the half-closed interval [start, end) where the
   * value is the negation of the key.
   */
  protected void warmUp(Map<Integer, Integer> map, int start, int end) {
    for (Integer i = start; i < end; i++) {
      assertThat(map.put(i, -i), is(nullValue()));
    }
  }
}
