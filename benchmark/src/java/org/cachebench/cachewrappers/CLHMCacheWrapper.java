package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import java.util.Map;

/**
 * An implementation that delegates to a {@link ConcurrentLinkedHashMap}.
 *
 * @author Adam Zell
 */
public final class CLHMCacheWrapper extends AbstractCacheWrapper {
  private static final String INITIAL_CAPACITY_PARAM = "clhm.initialCapacity";
  private static final String MAXIMUM_CAPACITY_PARAM = "clhm.maximumCapacity";
  private static final String CONCURRENCY_LEVEL_PARAM = "clhm.concurrencyLevel";

  private Map<Object, Object> map;
  private int maximumCapacity;

  @Override
  public void initialize(Map<String, String> params) {
    maximumCapacity = Integer.parseInt(params.get(MAXIMUM_CAPACITY_PARAM));
    map = new CacheBuilder()
        .maximumCapacity(maximumCapacity)
        .initialCapacity(Integer.parseInt(params.get(INITIAL_CAPACITY_PARAM)))
        .concurrencyLevel(Integer.parseInt(params.get(CONCURRENCY_LEVEL_PARAM)))
        .makeCache(Cache.ConcurrentLinkedHashMap);
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
