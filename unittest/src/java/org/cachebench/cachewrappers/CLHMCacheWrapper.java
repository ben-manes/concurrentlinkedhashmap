package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * An implementation that delegates to a {@link ConcurrentLinkedHashMap}.
 *
 * @author Adam Zell
 */
public final class CLHMCacheWrapper extends AbstractCacheWrapper {
  private static final String MAX_CAPACITY_PARAM = "clhm.maximumCapacity";
  private static final String CONCURRENCY_LEVEL_PARAM = "clhm.concurrencyLevel";
  
  private Map<Object, Object> cache;
  private int capacity;

  @Override
  public void initialize(Map<String, String> params) {
    capacity = Integer.parseInt(params.get(MAX_CAPACITY_PARAM));
    int concurrencyLevel = Integer.parseInt(params.get(CONCURRENCY_LEVEL_PARAM));
    cache = Cache.CONCURRENT_LINKED_HASH_MAP.create(capacity, concurrencyLevel);
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
