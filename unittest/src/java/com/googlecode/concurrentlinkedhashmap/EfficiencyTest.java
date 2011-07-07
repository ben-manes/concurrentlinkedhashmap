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

import static com.googlecode.concurrentlinkedhashmap.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.Benchmarks.determineEfficiency;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.Cache.Policy;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;

import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A unit-test and benchmark for evaluating the cache's hit rate.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EfficiencyTest extends AbstractTest {

  @Test(groups = "development", dataProvider = "builder")
  public void efficiency_lru(Builder<String, String> builder) {
    Map<String, String> expected = new CacheBuilder()
        .maximumCapacity(capacity())
        .makeCache(Cache.LinkedHashMap_Lru_Sync);
    Generator generator = new ScrambledZipfianGenerator(10 * capacity());
    List<String> workingSet = createWorkingSet(generator, 10 * capacity());

    int hitExpected = determineEfficiency(expected, workingSet);
    debug(efficiencyOf(Cache.LinkedHashMap_Lru_Sync.toString(),
        ImmutableMap.of(hitExpected, workingSet.size())));

    int hitActual = determineEfficiency(builder.build(), workingSet);
    debug(efficiencyOf(Cache.ConcurrentLinkedHashMap.toString(),
        ImmutableMap.of(hitActual, workingSet.size())));

    assertThat(hitActual, is(hitExpected));
  }

  /** Compares the hit rate of different cache implementations. */
  @Test(groups = "efficiency")
  public void efficency_compareAlgorithms() {
    Generator generator = new ScrambledZipfianGenerator(5 * capacity());
    List<String> workingSet = createWorkingSet(generator, 50 * capacity());
    List<String> workingSet2 = createWorkingSet(generator, 100 * capacity());
    List<String> workingSet3 = createWorkingSet(generator, 150 * capacity());

    Set<Policy> seen = EnumSet.noneOf(Policy.class);
    for (Cache cache : Cache.values()) {
      if (!seen.add(cache.policy())) {
        continue;
      }
      Map<String, String> map = new CacheBuilder()
          .maximumCapacity(capacity())
          .makeCache(cache);
      info(efficiencyOf(cache.policy().toString(), ImmutableMap.<Integer, Integer>of(
          determineEfficiency(map, workingSet), workingSet.size(),
          determineEfficiency(map, workingSet2), workingSet2.size(),
          determineEfficiency(map, workingSet3), workingSet3.size())));
    }
  }

  private static String efficiencyOf(String name, Map<Integer, Integer> runs) {
    StringBuilder builder = new StringBuilder(name + ":\n");
    for (Entry<Integer, Integer> run : runs.entrySet()) {
      int hitCount = run.getKey();
      int workingSetSize = run.getValue();
      int missCount = workingSetSize - hitCount;
      double hitRate = ((double) hitCount) / workingSetSize;
      double missRate = ((double) missCount) / workingSetSize;
      String str = String.format("hits=%s (%s percent), misses=%s (%s percent)%n",
          NumberFormat.getInstance().format(hitCount),
          NumberFormat.getPercentInstance().format(hitRate),
          NumberFormat.getInstance().format(missCount),
          NumberFormat.getPercentInstance().format(missRate));
      builder.append(str);
    }
    return builder.toString();
  }
}
