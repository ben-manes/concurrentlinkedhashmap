package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.distribution.Distribution;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A unit-test and benchmark for evaluating the cache's hit rate.
 *
 * @author bmanes@gmail.com (Ben Manes)
 */
public final class EfficiencyTest extends BaseTest {
  private Distribution distribution;
  private int size;

  public EfficiencyTest() {
    super(Integer.valueOf(System.getProperty("efficiency.maximumCapacity")));
  }

  @BeforeClass(groups = "efficiency")
  public void beforeEfficiency() {
    size = Integer.valueOf(System.getProperty("efficiency.workingSetSize"));
    distribution =
        Distribution.valueOf(System.getProperty("efficiency.distribution").toUpperCase());
  }

  @Test(groups = "development")
  public void lruEfficiency() {
    debug(" * Lru-efficency: START");
    ConcurrentLinkedHashMap<Long, Long> actual = create(capacity);
    Map<Long, Long> expected = Cache.SYNC_LRU.create(capacity, capacity, 1);

    List<Long> workingSet = createWorkingSet(Distribution.EXPONENTIAL, 10 * capacity);
    float hitExpected = determineEfficiency(expected, workingSet);
    float hitActual = determineEfficiency(actual, workingSet);
    assertTrue(hitExpected > 0);
    assertTrue(hitActual > 0);
    validator.state(actual);

    float expectedRate = 100 * hitActual/workingSet.size();
    float actualRate =  100 * hitActual/workingSet.size();
    debug("hit rate: expected=%s, actual=%s", expectedRate, actualRate);
  }

  /**
   * Compares the hit rate of different cache implementations.
   */
  @Test(groups = "efficiency")
  public void efficency() {
    List<Long> workingSet = createWorkingSet(distribution, size);
    debug("WorkingSet:\n%s", workingSet);
    for (Cache type : Cache.values()) {
      Map<Long, Long> cache = type.create(capacity, size, 1);
      double hits = determineEfficiency(cache, workingSet);
      double misses = size - hits;
      info("%s: hits=%s (%s percent), misses=%s (%s percent)", type,
           NumberFormat.getInstance().format(hits),
           NumberFormat.getPercentInstance().format(hits / size),
           NumberFormat.getInstance().format(misses),
           NumberFormat.getPercentInstance().format(misses / size));
    }
  }

  /**
   * Creates a random working set based on the distribution.
   *
   * @param distribution The distribution type to use.
   * @param size         The size of the working set.
   * @return A random working set.
   */
  private List<Long> createWorkingSet(Distribution distribution, int size) {
    Callable<Double> algorithm = distribution.getAlgorithm();
    List<Long> workingSet = new ArrayList<Long>(size);
    for (int i = 0; i < size; i++) {
      try {
        workingSet.add(Math.round(algorithm.call()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return workingSet;
  }

  /**
   * Determines the hit-rate of the cache.
   *
   * @param cache      The self-evicting map.
   * @param workingSet The request working set.
   * @return The hit-rate.
   */
  private int determineEfficiency(Map<Long, Long> cache, List<Long> workingSet) {
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
