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

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;
import static com.googlecode.concurrentlinkedhashmap.IsValidLinkedDeque.validLinkedDeque;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * A testing harness for simplifying the unit tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public abstract class AbstractTest {
  private static boolean debug;
  private long capacity;

  @Mock protected EvictionListener<Integer, Integer> listener;
  @Captor protected ArgumentCaptor<Runnable> catchUpTask;
  @Mock protected ScheduledExecutorService executor;
  @Mock protected Weigher<Integer> weigher;

  /** Retrieves the maximum weighted capacity to build maps with. */
  protected final long capacity() {
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
  public static void initSuite(@Optional("false") boolean debugMode) {
    debug = debugMode;
  }

  @Parameters("capacity")
  @BeforeClass(alwaysRun = true)
  public void initClass(long capacity) {
    this.capacity = capacity;
    initMocks(this);
  }

  @AfterMethod(alwaysRun = true)
  public void validateIfSuccessful(ITestResult result) {
    try {
      if (result.isSuccess()) {
        for (Object param : result.getParameters()) {
          validate(param);
        }
      }
    } catch (AssertionError caught) {
      result.setStatus(ITestResult.FAILURE);
      result.setThrowable(caught);
    }
    initMocks(this);
  }

  /** Validates the state of the injected parameter. */
  @SuppressWarnings("unchecked")
  private static void validate(Object param) {
    if (param instanceof ConcurrentLinkedHashMap<?, ?>) {
      assertThat((ConcurrentLinkedHashMap<?, ?>) param, is(valid()));
    } else if (param instanceof AbstractLinkedDeque<?>) {
      assertThat((AbstractLinkedDeque<Object>) param, is(validLinkedDeque()));
    }
  }

  /* ---------------- Map providers -------------- */

  /** Provides a builder with the capacity set. */
  @DataProvider(name = "builder")
  public Object[][] providesBuilder() {
    return new Object[][] {
      { new Builder<Object, Object>().maximumWeightedCapacity(capacity()) },
//      { new Builder<Object, Object>().maximumWeightedCapacity(capacity()).lirsPolicy(true) }
    };
  }

  @DataProvider(name = "policies")
  public Object[][] providesPolicies() {
    return new Object[][] {{ false }, { true }};
  }

  /** Provides an empty map for test methods. */
  @DataProvider(name = "emptyMap")
  public Object[][] providesEmptyMap() {
    return new Object[][] {{ newEmptyMap(false) }, { newEmptyMap(true) }};
  }

  /** Creates a map with the default capacity. */
  protected <K, V> ConcurrentLinkedHashMap<K, V> newEmptyMap(boolean lirs) {
    return new Builder<K, V>()
        .maximumWeightedCapacity(capacity())
        .lirsPolicy(lirs)
        .build();
  }

  /** Provides a guarded map for test methods. */
  @DataProvider(name = "guardedMap")
  public Object[][] providesGuardedMap() {
    return new Object[][] {
      { newGuarded(false) },
      { newGuarded(true) }
    };
  }

  @DataProvider(name = "guardedMap_lru")
  public Object[][] providesGuardedMap_lru() {
    return new Object[][] {{ newGuarded(false) }};
  }

  /** Creates a map that fails if an eviction occurs. */
  protected <K, V> ConcurrentLinkedHashMap<K, V> newGuarded(boolean lirs) {
    EvictionListener<K, V> guardingListener = guardingListener();
    return new Builder<K, V>()
        .maximumWeightedCapacity(capacity())
        .listener(guardingListener)
        .lirsPolicy(lirs)
        .build();
  }

  /** Provides a warmed map for test methods. */
  @DataProvider(name = "warmedMap")
  public Object[][] providesWarmedMap() {
    return new Object[][] {
        { newWarmedMap(false) },
//        { newWarmedMap(true) }
    };
  }

  @DataProvider(name = "warmedMap_lru")
  public Object[][] providesWarmedMap_lru() {
    ConcurrentLinkedHashMap<Integer, Integer> map = newWarmedMap(false);
    return new Object[][] {{ map, map.policy }};
  }

  /** Creates a map with warmed to capacity. */
  protected ConcurrentLinkedHashMap<Integer, Integer> newWarmedMap(boolean lirs) {
    ConcurrentLinkedHashMap<Integer, Integer> map = newEmptyMap(lirs);
    warmUp(map, 0, capacity());
    return map;
  }

  @DataProvider(name = "emptyWeightedMap")
  public Object[][] providesEmptyWeightedMap() {
    return new Object[][] {{ newEmptyWeightedMap() }};
  }

  private <K, V> ConcurrentLinkedHashMap<K, Iterable<V>> newEmptyWeightedMap() {
    return new Builder<K, Iterable<V>>()
        .maximumWeightedCapacity(capacity())
        .weigher(Weighers.<V>iterable())
        .build();
  }

  @DataProvider(name = "guardedWeightedMap")
  public Object[][] providesGuardedWeightedMap() {
    return new Object[][] {{ newGuardedWeightedMap() }};
  }

  private <K, V> ConcurrentLinkedHashMap<K, Iterable<V>> newGuardedWeightedMap() {
    EvictionListener<K, Iterable<V>> guardingListener = guardingListener();
    return new Builder<K, Iterable<V>>()
        .maximumWeightedCapacity(capacity())
        .weigher(Weighers.<V>iterable())
        .listener(guardingListener)
        .build();
  }

  @SuppressWarnings("unchecked")
  private <K, V> EvictionListener<K, V> guardingListener() {
    EvictionListener<K, V> guardingListener = (EvictionListener<K, V>) listener;
    doThrow(new AssertionError()).when(guardingListener)
        .onEviction(Mockito.<K>any(), Mockito.<V>any());
    return guardingListener;
  }

  /* ---------------- Weigher providers -------------- */

  @DataProvider(name = "singletonEntryWeigher")
  public Object[][] providesSingletonEntryWeigher() {
    return new Object[][] {{ Weighers.entrySingleton() }};
  }

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

  /* ---------------- Utility methods ------------- */

  /**
   * Populates the map with the half-closed interval [start, end) where the
   * value is the negation of the key.
   */
  protected static void warmUp(Map<Integer, Integer> map, int start, long end) {
    for (Integer i = start; i < end; i++) {
      assertThat(map.put(i, -i), is(nullValue()));
    }
  }
}
