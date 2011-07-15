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

import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.determineEfficiency;

import com.googlecode.concurrentlinkedhashmap.AbstractTest;
import com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.EfficiencyRun;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.Cache.Policy;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;

import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This benchmark compares the hit rate of different cache implementations.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EfficiencyBenchmark extends AbstractTest {

  @Test(groups = "efficiency")
  public void benchmark() {
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
      info(efficiencyOf(cache.policy().toString(),
          determineEfficiency(map, workingSet),
          determineEfficiency(map, workingSet2),
          determineEfficiency(map, workingSet3)));
    }
  }

  public static String efficiencyOf(String name, EfficiencyRun... runs) {
    StringBuilder builder = new StringBuilder(name + ":\n");
    for (EfficiencyRun run : runs) {
      builder.append(run);
    }
    return builder.toString();
  }
}
