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

import com.google.caliper.Param;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import java.util.concurrent.ConcurrentMap;

/**
 * A benchmark for the {@link java.util.concurrent.ConcurrentMap} interface.
 * These benchmarks evaluate single-threaded performance.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class ConcurrentMapBenchmark extends SimpleBenchmark {
  private static final int MASK = (2 << 10) - 1;
  private static final Integer[] ints;

  static {
    ints = new Integer[MASK + 1];
    for (int i = 0; i < ints.length; i++) {
      ints[i] = i;
    }
  }

  @Param({
    "MapMaker",
    "ConcurrentHashMap",
    "LinkedHashMap_Lru_Sync",
    "ConcurrentLinkedHashMap"})
  private Cache cacheType;

  @Param({"5", "50", "500", "5000", "50000", "500000"})
  private int maximumCapacity;

  @Param({"1", "4", "8", "16", "32", "64"})
  private int concurrencyLevel;

  private ConcurrentMap<Integer, Integer> map;
  private int index;

  @Override
  protected void setUp() {
    map = new CacheBuilder()
        .concurrencyLevel(concurrencyLevel)
        .initialCapacity(maximumCapacity)
        .maximumCapacity(maximumCapacity)
        .makeCache(cacheType);

    for (int i = 0; i < ints.length; i++) {
      map.put(ints[i], ints[i]);
    }
  }

  public int timeGet(final int reps) {
    int dummy = 0;
    for (int i = 0; i < reps; i++) {
      dummy += (map.get(ints[index++ & MASK]) == null) ? 0 : 1;
    }
    return dummy;
  }

  /** Kick-start the benchmark. */
  public static void main(String[] args) {
    Runner.main(ConcurrentMapBenchmark.class, args);
  }
}
