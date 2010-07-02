package com.googlecode.concurrentlinkedhashmap.caches;

import static java.util.Collections.synchronizedMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory for creating caches for use within tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum Cache {

  /**
   * A concurrent linked hashmap, using LRU eviction.
   */
  CONCURRENT_LINKED_HASH_MAP() {
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
   * ConcurrentMap and ConcurrentQueue, using FIFO eviction.
   */
  CONCURRENT_FIFO() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new ConcurrentFifoMap<K, V>(capacity);
    }
  },

  /**
   * ConcurrentMap and ConcurrentQueue, using second-chance FIFO eviction.
   */
  SECOND_CHANCE() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new SecondChanceMap<K, V>(capacity);
    }
  },

  /**
   * ConcurrentMap and ConcurrentQueue and key-value links, using second-chance
   * FIFO eviction
   */
  SECOND_CHANCE_LINKED() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new SecondChanceLinkedMap<K, V>(capacity, concurrencyLevel);
    }
  },

  /**
   * LinkedHashMap in FIFO eviction, guarded by read/write lock.
   */
  RW_FIFO() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new LockMap<K, V>(false, capacity);
    }
  },

  /**
   * LinkedHashMap in LRU eviction, guarded by lock.
   */
  LOCK_LRU() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new LockMap<K, V>(true, capacity);
    }
  },

  /**
   * LinkedHashMap in FIFO eviction, guarded by synchronized monitor.
   */
  SYNC_FIFO() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return synchronizedMap(new UnsafeMap<K, V>(false, capacity));
    }
  },

  /**
   * LinkedHashMap in LRU eviction, guarded by synchronized monitor.
   */
  SYNC_LRU() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return synchronizedMap(new UnsafeMap<K, V>(true, capacity));
    }
  },

  /**
   * ConcurrentMap with no eviction policy.
   */
  CONCURRENT_HASH_MAP() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new ConcurrentHashMap<K, V>(capacity, 0.75f, concurrencyLevel);
    }
  },

  /**
   * Ehcache, using FIFO eviction.
   */
  EHCACHE_FIFO() {
    @Override
    public <K, V> Map<K, V>  create(int capacity, int concurrencyLevel) {
      return new EhcacheMap<K, V>(false, capacity);
    }
  },

  /**
   * Ehcache, using LRU eviction.
   */
  EHCACHE_LRU() {
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
