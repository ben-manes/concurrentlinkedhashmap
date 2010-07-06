package com.googlecode.concurrentlinkedhashmap.caches;

import static java.util.Collections.synchronizedMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.ProductionMap.EvictionPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory for creating caches for use within tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum Cache {

  /**
   * A concurrent linked hash map.
   */
  ConcurrentLinkedHashMap() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new Builder<K, V>()
          .initialCapacity(capacity)
          .maximumWeightedCapacity(capacity)
          .concurrencyLevel(concurrencyLevel)
          .build();
    }
  },

  /**
   * A concurrent map using a first-in, first-out eviction policy.
   */
  Concurrent_Fifo() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return ProductionMap.create(EvictionPolicy.FIFO, capacity, concurrencyLevel);
    }
  },

  /**
   * A concurrent map using a second chance first-in, first-out eviction policy.
   */
  Concurrent_SecondChanceFifo() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return ProductionMap.create(EvictionPolicy.SECOND_CHANCE, capacity, concurrencyLevel);
    }
  },

  /**
   * A concurrent map using an eager lock-based LRU eviction policy.
   */
  Concurrent_Lru() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return ProductionMap.create(EvictionPolicy.LRU, capacity, concurrencyLevel);
    }
  },

  /**
   * LinkedHashMap in FIFO eviction, guarded by read/write lock.
   */
  LinkedHashMap_Fifo_Lock() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new LockMap<K, V>(false, capacity);
    }
  },

  /**
   * LinkedHashMap in LRU eviction, guarded by lock.
   */
  LinkedHashMap_Lru_Lock() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new LockMap<K, V>(true, capacity);
    }
  },

  /**
   * LinkedHashMap in FIFO eviction, guarded by synchronized monitor.
   */
  LinkedHashMap_Fifo_Sync() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return synchronizedMap(new UnsafeMap<K, V>(false, capacity));
    }
  },

  /**
   * LinkedHashMap in LRU eviction, guarded by synchronized monitor.
   */
  LinkedHashMap_Lru_Sync() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return synchronizedMap(new UnsafeMap<K, V>(true, capacity));
    }
  },

  /**
   * ConcurrentMap with no eviction policy (unbounded).
   */
  ConcurrentHashMap() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new ConcurrentHashMap<K, V>(capacity, 0.75f, concurrencyLevel);
    }
  },

  /**
   * Ehcache, using FIFO eviction.
   */
  Ehcache_Fifo() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new EhcacheMap<K, V>(false, capacity);
    }
  },

  /**
   * Ehcache, using LRU eviction.
   */
  Ehcache_Lru() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new EhcacheMap<K, V>(true, capacity);
    }
  };

  /**
   * Creates the local cache instance.
   *
   * @param capacity the cache's capacity.
   * @param concurrencyLevel the number of concurrent writes
   * @return a cache provided under a {@link Map} interface
   */
  public abstract <K, V> Map<K, V>  create(int capacity, int concurrencyLevel);
}
