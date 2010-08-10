package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * An implementation that delegates to a ConcurrentHashMap.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CHMCacheWrapper extends AbstractCacheWrapper {
  private static final String INITIAL_CAPACITY_PARAM = "chm.initialCapacity";
  private static final String CONCURRENCY_LEVEL_PARAM = "chm.concurrencyLevel";

  private Map<Object, Object> cache;

  @Override
  public void initialize(Map<String, String> params) {
    int initialCapacity = Integer.parseInt(params.get(INITIAL_CAPACITY_PARAM));
    int concurrencyLevel = Integer.parseInt(params.get(CONCURRENCY_LEVEL_PARAM));
    cache = Cache.CONCURRENT_HASH_MAP.create(initialCapacity, concurrencyLevel);
  }

  @Override
  public void setUp() throws Exception {
    cache.clear();
  }

  @Override
  protected int capacity() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected Map<Object, Object> delegate() {
    return cache;
  }
}
