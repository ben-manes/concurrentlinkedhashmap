package com.googlecode.concurrentlinkedhashmap.benchmark;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.caliper.Param;
import com.google.caliper.Runner;
import com.google.common.collect.MapMaker;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * A benchmark of comparing the read/write performance at different ratios.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class GetPutBenchmark extends ConcurrentBenchmark {
  private Map<Integer, Integer> clhm;
  private Map<Integer, Integer> chm;
  private Map<Integer, Integer> lhm;

  @Param int numberOfThreads;
  @Param int initialCapacity;
  @Param int maximumCapacity;
  @Param int concurrencyLevel;
  @Param int readRatio;

  // TODO(bmanes): Add read/write ratio, generate working set, etc.

  @Override
  protected void benchmarkSetUp() {
    checkArgument((readRatio >= 0) && (readRatio <= 100), "Read ratio must between zero and 100%");
    clhm = new Builder<Integer, Integer>()
        .initialCapacity(initialCapacity)
        .concurrencyLevel(concurrencyLevel)
        .maximumWeightedCapacity(maximumCapacity)
        .build();
    chm = new MapMaker()
        .concurrencyLevel(concurrencyLevel)
        .initialCapacity(initialCapacity)
        .makeMap();
    lhm = Cache.SYNC_LRU.create(maximumCapacity, concurrencyLevel);
  }

  public void timeConcurrentLinkedHashMap(final int reps) {
    concurrent(new Runnable() {
      @Override public void run() {
        for (int i = 0; i < reps; i++) {
          clhm.get(i);
        }
      }
    });
  }

  public void timeConcurrentHashMap(final int reps) {
    concurrent(new Runnable() {
      @Override public void run() {
        for (int i = 0; i < reps; i++) {
          chm.get(i);
        }
      }
    });
  }

  public void timeLinkedHashMap(final int reps) {
    concurrent(new Runnable() {
      @Override public void run() {
        for (int i = 0; i < reps; i++) {
          lhm.get(i);
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
