package com.googlecode.concurrentlinkedhashmap.benchmark;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.caliper.Param;
import com.google.caliper.Runner;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * A benchmark comparing the read/write performance at different ratios.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class GetPutBenchmark extends ConcurrentBenchmark {
  @Param({"CONCURRENT_LINKED_HASH_MAP", "CONCURRENT_HASH_MAP", "SYNC_LRU"})
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
    map = cache.create(maximumCapacity, concurrencyLevel);
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
