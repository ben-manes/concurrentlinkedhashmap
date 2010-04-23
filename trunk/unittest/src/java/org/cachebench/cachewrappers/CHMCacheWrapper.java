package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation that delegates to a ConcurrentHashMap.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CHMCacheWrapper extends AbstractCacheWrapper {
  private Map<Object, Object> cache;
  private int concurrencyLevel;
  private int capacity;

  @Override
  public void init(Map parameters) throws Exception {
    concurrencyLevel = 16;
    capacity = 5000;
  }

  @Override
  public void setUp() throws Exception {
    cache = Cache.CONCURRENT_HASH_MAP.create(capacity, concurrencyLevel);
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
