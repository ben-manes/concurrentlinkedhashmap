package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import java.util.Map;

/**
 * An implementation that delegates to a {@link java.util.LinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class LHMCacheWrapper extends AbstractCacheWrapper {
  private static final String INITIAL_CAPACITY_PARAM = "lhm.initialCapacity";
  private static final String MAXIMUM_CAPACITY_PARAM = "lhm.maximumCapacity";

  private Map<Object, Object> map;
  private int maximumCapacity;

  @Override
  public void initialize(Map<String, String> params) {
    maximumCapacity = Integer.parseInt(params.get(MAXIMUM_CAPACITY_PARAM));
    map = new CacheBuilder()
        .concurrencyLevel(1) // ignored
        .maximumCapacity(maximumCapacity)
        .initialCapacity(Integer.parseInt(params.get(INITIAL_CAPACITY_PARAM)))
        .makeCache(Cache.LinkedHashMap_Lru_Sync);
  }

  @Override
  public void setUp() throws Exception {
    map.clear();
  }

  @Override
  protected int capacity() {
    return maximumCapacity;
  }

  @Override
  protected Map<Object, Object> delegate() {
    return map;
  }
}
