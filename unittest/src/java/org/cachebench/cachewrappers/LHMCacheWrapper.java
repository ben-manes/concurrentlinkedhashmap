package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;

import java.util.Map;

/**
 * An implementation that delegates to a {@link java.util.LinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class LHMCacheWrapper extends AbstractCacheWrapper {
  private Map<Object, Object> cache;
  private int capacity;

  @Override
  public void init(Map parameters) throws Exception {
    capacity = 5000;
  }

  @Override
  public void setUp() throws Exception {
    cache = Cache.SYNC_LRU.create(capacity, 1);
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
