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

import com.googlecode.concurrentlinkedhashmap.caches.CacheFactory;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.googlecode.concurrentlinkedhashmap.CacheType;
import com.googlecode.concurrentlinkedhashmap.CacheType.Policy;

/**
 * This benchmark compares the hit rate of different cache implementations.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EfficiencyBenchmark {

  @Test(groups = "efficiency")
  @Parameters({"capacity", "passes", "generatorMultipler", "workingSetMultiplier"})
  public void benchmark(int capacity, int passes, int generatorMultipler,
      int workingSetMultiplier) {
    Generator generator = new ScrambledZipfianGenerator(generatorMultipler * capacity);
    List<List<String>> workingSets = Lists.newArrayList();
    for (int i = 1; i <= passes; i++) {
      int size = i * workingSetMultiplier * capacity;
      workingSets.add(createWorkingSet(generator, size));
    }

    Set<Policy> seen = EnumSet.noneOf(Policy.class);
    for (CacheType cache : CacheType.values()) {
      if (!seen.add(cache.policy())) {
        continue;
      }
      Map<String, String> map = new CacheFactory()
          .maximumCapacity(capacity)
          .makeCache(cache);
      System.out.println(cache.policy().toString() + ":");
      for (List<String> workingSet : workingSets) {
        System.out.println(determineEfficiency(map, workingSet));
      }
    }
  }
}
