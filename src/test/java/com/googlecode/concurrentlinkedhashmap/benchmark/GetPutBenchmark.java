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
package com.googlecode.concurrentlinkedhashmap.benchmark;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;
import com.googlecode.concurrentlinkedhashmap.CacheType;
import com.googlecode.concurrentlinkedhashmap.caches.CacheFactory;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;
import org.testng.annotations.Test;

import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.createWorkingSet;

/**
 * This benchmark evaluates single-threaded performance.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class GetPutBenchmark extends Benchmark {
  private static final int MASK = (2 << 10) - 1;
  private static final List<Integer> ints =
    createWorkingSet(new ScrambledZipfianGenerator(MASK + 1), MASK + 1);
  private static final Integer DUMMY = 1;

  @Param({
    "Guava",
    "ConcurrentHashMap",
    "LinkedHashMap_Lru_Lock",
    "ConcurrentLinkedHashMap"})
  private CacheType cache;

  private ConcurrentMap<Integer, Integer> map;
  private int index;

  @Override
  protected void setUp() {
    map = new CacheFactory()
        .maximumCapacity(Integer.MAX_VALUE)
        .makeCache(cache);
    for (int i = 0; i < ints.size(); i++) {
      map.put(ints.get(i), DUMMY);
    }
  }

  public void timeGet(final int reps) {
    for (int i = 0; i < reps; i++) {
      map.get(ints.get(index++ & MASK));
    }
  }

  public void timePut(final int reps) {
    for (int i = 0; i < reps; i++) {
      map.put(ints.get(index++ & MASK), DUMMY);
    }
  }

  @Test(groups = "caliper")
  public static void benchmark() {
    CaliperMain.main(GetPutBenchmark.class, new String[] {});
  }
}
