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
import static com.google.common.base.Preconditions.checkArgument;

import com.google.caliper.Param;
import com.google.caliper.Runner;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import java.util.Map;


/**
 * A benchmark comparing the read/write performance at different ratios.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class GetPutBenchmark extends ConcurrentBenchmark {
  @Param({
    "ConcurrentHashMap",
    "LinkedHashMap_Lru_Sync",
    "ConcurrentLinkedHashMap"})
  Cache cache;

  @Param int numberOfThreads;
  @Param int initialCapacity;
  @Param int maximumCapacity;
  @Param int concurrencyLevel;
  @Param int readRatio;

  private Map<Integer, Integer> map;

  // TODO(bmanes): Add read/write ratio, generate working set, etc.

  @Override
  protected void benchmarkSetUp() {
    checkArgument((readRatio >= 0) && (readRatio <= 100), "Read ratio must between zero and 100%");
    map = new CacheBuilder()
        .concurrencyLevel(concurrencyLevel)
        .initialCapacity(initialCapacity)
        .maximumCapacity(maximumCapacity)
        .makeCache(cache);
  }

  public void timeReadWrite(final int reps) {
    concurrent(new Runnable() {
      @Override public void run() {
        for (int i = 0; i < reps; i++) {
          map.get(i);
        }
      }
    });
  }

  @Override
  protected int getNumberOfThreads() {
    return numberOfThreads;
  }

  /** Kick-start the benchmark. */
  public static void main(String[] args) {
    Runner.main(GetPutBenchmark.class, args);
  }
}
