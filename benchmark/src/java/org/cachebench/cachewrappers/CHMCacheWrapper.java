package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import java.util.Map;

/**
 * An implementation that delegates to a ConcurrentHashMap.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CHMCacheWrapper extends AbstractCacheWrapper {
  private static final String INITIAL_CAPACITY_PARAM = "chm.initialCapacity";
  private static final String CONCURRENCY_LEVEL_PARAM = "chm.concurrencyLevel";

  private Map<Object, Object> map;

  @Override
  public void initialize(Map<String, String> params) {
    map = new CacheBuilder()
        .initialCapacity(Integer.parseInt(params.get(INITIAL_CAPACITY_PARAM)))
        .concurrencyLevel(Integer.parseInt(params.get(CONCURRENCY_LEVEL_PARAM)))
        .maximumCapacity(Integer.MAX_VALUE) // ignored
        .makeCache(Cache.ConcurrentHashMap);
  }

  public void setUp() throws Exception {
    map.clear();
  }

  @Override
  protected int capacity() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected Map<Object, Object> delegate() {
    return map;
  }
}
