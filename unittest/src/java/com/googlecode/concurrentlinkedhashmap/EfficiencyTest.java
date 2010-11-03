package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.Benchmarks.determineEfficiency;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.distribution.Distribution;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

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

  @Test(groups = "development", dataProvider = "builder")
  public void efficiency_lru(Builder<Long, Long> builder) {
    Map<Long, Long> expected = new CacheBuilder()
        .maximumCapacity(capacity())
        .makeCache(Cache.LinkedHashMap_Lru_Sync);
    List<Long> workingSet = createWorkingSet(Distribution.Exponential, 10 * capacity());
    float hitExpected = determineEfficiency(expected, workingSet);
    float hitActual = determineEfficiency(builder.build(), workingSet);
    assertThat((int) hitExpected, is(greaterThan(0)));
    assertThat((int) hitActual, is(greaterThan(0)));

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
    for (Cache cache : Cache.values()) {
      Map<Long, Long> map = new CacheBuilder()
          .maximumCapacity(capacity())
          .makeCache(cache);
      double hits = determineEfficiency(map, workingSet);
      double misses = size - hits;
      info("%s: hits=%s (%s percent), misses=%s (%s percent)", cache,
           NumberFormat.getInstance().format(hits),
           NumberFormat.getPercentInstance().format(hits / size),
           NumberFormat.getInstance().format(misses),
           NumberFormat.getPercentInstance().format(misses / size));
    }
  }
}
