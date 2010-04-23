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
  private Map<Object, Object> cache;
  private int capacity;

  @Override
  public void init(Map parameters) throws Exception {
//    InputStream stream =
//        getClass().getClassLoader().getResourceAsStream((String) parameters.get("config"));
//    Properties props = new Properties();
//
//    props.load(stream);
//    stream.close();
//
//    level = Integer.parseInt(props.getProperty("clhm.concurrencyLevel"));
//    capacity = Integer.parseInt(props.getProperty("clhm.maximumCapacity"));
    int concurrencylevel = 16;
    capacity = 5000;

    cache = Cache.CONCURRENT_LINKED_HASH_MAP.create(capacity, concurrencylevel);
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
