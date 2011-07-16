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

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.IntegerGenerator;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A set of utilities for writing benchmarks.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class Benchmarks {

  private Benchmarks() {}

  /**
   * Creates a random working set based on the distribution.
   *
   * @param generator the distribution generator
   * @param size the size of the working set
   * @return a random working set
   */
  public static List<String> createWorkingSet(Generator generator, int size) {
    List<String> workingSet = newArrayListWithCapacity(size);
    for (int i = 0; i < size; i++) {
      workingSet.add(generator.nextString());
    }
    return workingSet;
  }

  /**
   * Creates a random working set based on the distribution.
   *
   * @param generator the distribution generator
   * @param size the size of the working set
   * @return a random working set
   */
  public static List<Integer> createWorkingSet(IntegerGenerator generator, int size) {
    Integer[] ints = new Integer[size];
    for (int i = 0; i < ints.length; i++) {
      ints[i] = generator.nextInt();
    }
    return Arrays.asList(ints);
  }

  /**
   * Based on the passed in working set, creates N shuffled variants.
   *
   * @param samples the number of variants to create
   * @param workingSet the base working set to build from
   */
  public static <T> List<List<T>> shuffle(int samples, Collection<T> workingSet) {
    List<List<T>> sets = newArrayListWithCapacity(samples);
    for (int i = 0; i < samples; i++) {
      List<T> set = newArrayList(workingSet);
      Collections.shuffle(set);
      sets.add(set);
    }
    return sets;
  }

  /**
   * Determines the hit/miss rate of a cache.
   *
   * @param cache the self-evicting map
   * @param workingSet the request working set
   * @return the efficiency of the execution run
   */
  public static <T> EfficiencyRun determineEfficiency(Map<T, T> cache, List<T> workingSet) {
    int hits = 0;
    for (T key : workingSet) {
      if (cache.get(key) == null) {
        cache.put(key, key);
      } else {
        hits++;
      }
    }
    return EfficiencyRun.of(hits, workingSet.size());
  }

  public static final class EfficiencyRun {
    public final int hitCount;
    public final int missCount;
    public final double hitRate;
    public final double missRate;
    public final int workingSetSize;

    private EfficiencyRun(int hitCount, int workingSetSize) {
      this.workingSetSize = workingSetSize;
      this.hitCount = hitCount;
      this.missCount = workingSetSize - hitCount;
      this.hitRate = ((double) hitCount) / workingSetSize;
      this.missRate = ((double) missCount) / workingSetSize;
    }

    public static EfficiencyRun of(int hitCount, int workingSetSize) {
      return new EfficiencyRun(hitCount, workingSetSize);
    }

    @Override
    public String toString() {
      return String.format("hits=%s (%s percent), misses=%s (%s percent)",
        NumberFormat.getInstance().format(hitCount),
        NumberFormat.getPercentInstance().format(hitRate),
        NumberFormat.getInstance().format(missCount),
        NumberFormat.getPercentInstance().format(missRate));
    }
  }
}
