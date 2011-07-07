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

import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;
import static com.googlecode.concurrentlinkedhashmap.IsValidLinkedDeque.validLinkedDeque;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.fail;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A testing harness for simplifying the unit tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public abstract class AbstractTest {
  private static boolean debug;
  private int capacity;

  @Mock protected EvictionListener<Integer, Integer> listener;
  @Captor protected ArgumentCaptor<Runnable> catchUpTask;
  @Mock protected ScheduledExecutorService executor;
  @Mock protected Weigher<Integer> weigher;

  /** Retrieves the maximum weighted capacity to build maps with. */
  protected final int capacity() {
    return capacity;
  }

  /* ---------------- Logging methods -------------- */

  protected static void info(String message, Object... args) {
    if (args.length == 0) {
      System.out.println(message);
    } else {
      System.out.printf(message + "\n", args);
    }
  }

  protected static void debug(String message, Object... args) {
    if (debug) {
      info(message, args);
    }
  }

  /* ---------------- Testing aspects -------------- */

  @Parameters("debug")
  @BeforeSuite(alwaysRun = true)
  static void initSuite(boolean debugMode) {
    debug = debugMode;
  }

  @Parameters("capacity")
  @BeforeClass(alwaysRun = true)
  void initClass(int capacity) {
    info("\nRunning %s...\n", getClass().getSimpleName());
    this.capacity = capacity;
  }

  @BeforeMethod(alwaysRun = true)
  void initMethod() {
    initMocks(this);
  }

  @AfterMethod(alwaysRun = true)
  void validateIfSuccessful(ITestResult result) {
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
  @SuppressWarnings("unchecked")
  private static void validate(Object param) {
    if (param instanceof ConcurrentLinkedHashMap<?, ?>) {
      assertThat((ConcurrentLinkedHashMap<?, ?>) param, is(valid()));
    }
    if (param instanceof LinkedDeque<?>) {
      assertThat((LinkedDeque<?>) param, is(validLinkedDeque()));
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

  @DataProvider(name = "guardedWeightedMap")
  public Object[][] providesGuardedWeightedMap() {
    return new Object[][] {{ newWeightedMap() }};
  }

  protected <K, V> ConcurrentLinkedHashMap<K, Iterable<V>> newWeightedMap() {
    return new Builder<K, Iterable<V>>()
        .weigher(Weighers.<V>iterable())
        .maximumWeightedCapacity(capacity())
        .build();
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

  /** A listener that fails if invoked. */
  private static final class GuardingListener<K, V>
    implements EvictionListener<K, V>, Serializable {

    @Override public void onEviction(Object key, Object value) {
      fail(String.format("Evicted %s=%s", key, value));
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
