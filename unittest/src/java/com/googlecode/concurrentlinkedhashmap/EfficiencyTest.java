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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;

import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * A unit-test and benchmark for evaluating the cache's hit rate.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EfficiencyTest extends AbstractTest {

  @Override
  protected int capacity() {
    return intProperty("efficiency.maximumCapacity");
  }

  @Test(groups = "development", dataProvider = "builder")
  public void efficiency_lru(Builder<String, String> builder) {
    Map<String, String> expected = new CacheBuilder()
        .maximumCapacity(capacity())
        .makeCache(Cache.LinkedHashMap_Lru_Sync);
    Generator generator = new ScrambledZipfianGenerator(10 * capacity());
    List<String> workingSet = createWorkingSet(generator, 10 * capacity());

    int hitExpected = determineEfficiency(expected, workingSet);
    debug(efficiencyOf(Cache.LinkedHashMap_Lru_Sync, hitExpected, workingSet));

    int hitActual = determineEfficiency(builder.build(), workingSet);
    debug(efficiencyOf(Cache.ConcurrentLinkedHashMap, hitActual, workingSet));

    assertThat(hitActual, is(hitExpected));
  }

  /** Compares the hit rate of different cache implementations. */
  @Test(groups = "efficiency")
  public void efficency_compareAlgorithms() {
    Generator generator = new ScrambledZipfianGenerator(5 * capacity());
    List<String> workingSet = createWorkingSet(generator, 50 * capacity());

    debug("WorkingSet:\n%s", workingSet);
    for (Cache cache : Cache.values()) {
      Map<String, String> map = new CacheBuilder()
          .maximumCapacity(capacity())
          .makeCache(cache);
      int hits = determineEfficiency(map, workingSet);
      info(efficiencyOf(cache, hits, workingSet));
    }
  }

  private static String efficiencyOf(Cache cache, int hits, List<String> workingSet) {
    int misses = workingSet.size() - hits;
    return String.format("%s:%nhits=%s (%s percent), misses=%s (%s percent)%n", cache,
         NumberFormat.getInstance().format(hits),
         NumberFormat.getPercentInstance().format(((double) hits) / workingSet.size()),
         NumberFormat.getInstance().format(misses),
         NumberFormat.getPercentInstance().format(((double) misses) / workingSet.size()));
  }
}
