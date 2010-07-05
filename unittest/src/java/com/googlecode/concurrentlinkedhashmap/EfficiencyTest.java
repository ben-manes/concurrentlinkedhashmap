package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.distribution.Distribution;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A unit-test and benchmark for evaluating the cache's hit rate.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EfficiencyTest extends BaseTest {
  private Distribution distribution;
  private int size;

  @Override
  protected int capacity() {
    return intProperty("efficiency.maximumCapacity");
  }

  @BeforeClass(groups = "efficiency")
  public void beforeEfficiency() {
    size = intProperty("efficiency.workingSetSize");
    distribution = enumProperty("efficiency.distribution", Distribution.class);
  }

  @Test(groups = "development", dataProvider = "emptyMap")
  public void efficiency_lru(ConcurrentLinkedHashMap<Long, Long> actual) {
    Map<Long, Long> expected = Cache.SYNC_LRU.create(capacity(), 1);

    List<Long> workingSet = createWorkingSet(Distribution.EXPONENTIAL, 10 * capacity());
    float hitExpected = determineEfficiency(expected, workingSet);
    float hitActual = determineEfficiency(actual, workingSet);
    assertTrue(hitExpected > 0);
    assertTrue(hitActual > 0);
    validator.checkValidState(actual);

    float expectedRate = 100 * hitActual/workingSet.size();
    float actualRate =  100 * hitActual/workingSet.size();
    debug("hit rate: expected=%s, actual=%s", expectedRate, actualRate);
  }

  /**
   * Compares the hit rate of different cache implementations.
   */
  @Test(groups = "efficiency")
  public void efficency_compareAlgorithms() {
    List<Long> workingSet = createWorkingSet(distribution, size);
    debug("WorkingSet:\n%s", workingSet);
    for (Cache type : Cache.values()) {
      Map<Long, Long> cache = type.create(capacity(), 1);
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
   * @param distribution the distribution type to use
   * @param size the size of the working set
   * @return a random working set
   */
  private List<Long> createWorkingSet(Distribution distribution, int size) {
    Callable<Double> algorithm = distribution.getAlgorithm();
    List<Long> workingSet = newArrayListWithCapacity(size);
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
   * @param cache the self-evicting map
   * @param workingSet the request working set
   * @return the hit-rate
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
