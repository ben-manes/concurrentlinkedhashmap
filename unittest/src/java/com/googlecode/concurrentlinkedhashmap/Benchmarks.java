package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

import com.google.common.base.Supplier;

import com.googlecode.concurrentlinkedhashmap.distribution.Distribution;

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
   * @param distribution the distribution type to use
   * @param size the size of the working set
   * @return a random working set
   */
  public static List<Long> createWorkingSet(Distribution distribution, int size) {
    Supplier<Double> algorithm = distribution.getAlgorithm();
    List<Long> workingSet = newArrayListWithCapacity(size);
    for (int i = 0; i < size; i++) {
      workingSet.add(Math.round(algorithm.get()));
    }
    return workingSet;
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
   * @return the hit rate
   */
  public static int determineEfficiency(Map<Long, Long> cache, List<Long> workingSet) {
    int hits = 0;
    for (Long key : workingSet) {
      if (cache.get(key) == null) {
        cache.put(key, 0L);
      } else {
        hits++;
      }
    }
    return hits;
  }
}
