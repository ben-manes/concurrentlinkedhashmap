package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * An implementation that delegates to a {@link java.util.LinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class LHMCacheWrapper extends AbstractCacheWrapper {
  private static final String MAX_CAPACITY_PARAM = "lhm.maximumCapacity";

  private Map<Object, Object> cache;
  private int capacity;

  @Override
  public void initialize(Map<String, String> params) {
    capacity = Integer.parseInt(params.get(MAX_CAPACITY_PARAM));
    cache = Cache.SYNC_LRU.create(capacity, 1);
  }

  @Override
  public void setUp() throws Exception {
    cache.clear();
  }

  @Override
  protected int capacity() {
    return capacity;
  }

  @Override
  protected Map<Object, Object> delegate() {
    return cache;
  }
}
