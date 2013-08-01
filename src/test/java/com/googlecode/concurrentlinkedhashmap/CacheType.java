/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.cache.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.BoundedLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.caches.BoundedLinkedHashMap.AccessOrder;
import com.googlecode.concurrentlinkedhashmap.caches.CacheConcurrentLIRS;
import com.googlecode.concurrentlinkedhashmap.caches.CacheFactory;
import com.googlecode.concurrentlinkedhashmap.caches.EhcacheMap;
import com.googlecode.concurrentlinkedhashmap.caches.LirsMap;
import com.googlecode.concurrentlinkedhashmap.caches.LockForwardingConcurrentMap;
import com.googlecode.concurrentlinkedhashmap.caches.ProductionMap;
import com.googlecode.concurrentlinkedhashmap.caches.ProductionMap.EvictionPolicy;
import com.googlecode.concurrentlinkedhashmap.caches.SynchronizedForwardingConcurrentMap;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * A collection of cache data structures that can be built.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum CacheType {
  /** A concurrent linked hash map. */
  ConcurrentLinkedHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new Builder<K, V>()
          .initialCapacity(builder.initialCapacity)
          .concurrencyLevel(builder.concurrencyLevel)
          .maximumWeightedCapacity(builder.maximumCapacity)
          .build();
    }
    @Override public Policy policy() {
      return Policy.LRU;
    }
  },

  /** A concurrent map using a first-in, first-out eviction policy. */
  Concurrent_Fifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new ProductionMap<K, V>(EvictionPolicy.FIFO, builder);
    }
    @Override public Policy policy() {
      return Policy.FIFO;
    }
  },

  /**
   * A concurrent map using a second chance first-in, first-out eviction policy.
   */
  Concurrent_SecondChanceFifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new ProductionMap<K, V>(EvictionPolicy.SECOND_CHANCE,  builder);
    }
    @Override public Policy policy() {
      return Policy.SECOND_CHANCE;
    }
  },

  /** A concurrent map using an eager lock-based LRU eviction policy. */
  Concurrent_Lru() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new ProductionMap<K, V>(EvictionPolicy.LRU, builder);
    }
    @Override public Policy policy() {
      return Policy.LRU;
    }
  },

  /** LinkedHashMap in FIFO eviction, guarded by a read/write lock. */
  LinkedHashMap_Fifo_RWLock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      ReadWriteLock lock = new ReentrantReadWriteLock();
      Map<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.FIFO, builder);
      return new LockForwardingConcurrentMap<K, V>(lock.readLock(), lock.writeLock(), delegate);
    }
    @Override public Policy policy() {
      return Policy.FIFO;
    }
  },

  /** LinkedHashMap in FIFO eviction, guarded by an exclusive lock. */
  LinkedHashMap_Fifo_Lock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      Lock lock = new ReentrantLock();
      Map<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.FIFO, builder);
      return new LockForwardingConcurrentMap<K, V>(lock, lock, delegate);
    }
    @Override public Policy policy() {
      return Policy.FIFO;
    }
  },

  /** LinkedHashMap in LRU eviction, guarded by an exclusive lock. */
  LinkedHashMap_Lru_Lock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      Lock lock = new ReentrantLock(); // LRU mutates on reads to update access order
      Map<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.LRU, builder);
      return new LockForwardingConcurrentMap<K, V>(lock, lock, delegate);
    }
    @Override public Policy policy() {
      return Policy.LRU;
    }
  },

  /** LinkedHashMap in FIFO eviction, guarded by synchronized monitor. */
  LinkedHashMap_Fifo_Sync() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      Map<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.FIFO, builder);
      return new SynchronizedForwardingConcurrentMap<K, V>(delegate);
    }
    @Override public Policy policy() {
      return Policy.FIFO;
    }
  },

  /** LinkedHashMap in LRU eviction, guarded by synchronized monitor. */
  LinkedHashMap_Lru_Sync() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      Map<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.LRU, builder);
      return new SynchronizedForwardingConcurrentMap<K, V>(delegate);
    }
    @Override public Policy policy() {
      return Policy.LRU;
    }
  },

  /** ConcurrentHashMap (unbounded). */
  ConcurrentHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new ConcurrentHashMap<K, V>(builder.initialCapacity, 0.75f, builder.concurrencyLevel);
    }
    @Override public Policy policy() {
      return Policy.UNBOUNDED;
    }
  },

  /** ConcurrentHashMapV8 (unbounded). */
  ConcurrentHashMapV8() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new ConcurrentHashMapV8<K, V>(
          builder.initialCapacity, 0.75f, builder.concurrencyLevel);
    }
    @Override public Policy policy() {
      return Policy.UNBOUNDED;
    }
  },

  /** Ehcache, using CLOCK eviction (alias for second chance). */
  Ehcache_Clock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.CLOCK, builder);
    }
    @Override public Policy policy() {
      return Policy.SECOND_CHANCE;
    }
  },

  /** Ehcache, using FIFO eviction. */
  Ehcache_Fifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.FIFO, builder);
    }
    @Override public Policy policy() {
      return Policy.FIFO;
    }
  },

  /** Ehcache, using LFU eviction. */
  Ehcache_Lfu() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.LFU, builder);
    }
    @Override public Policy policy() {
      return Policy.LFU;
    }
  },

  /** Ehcache, using LRU eviction. */
  Ehcache_Lru() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.LRU, builder);
    }
    @Override public Policy policy() {
      return Policy.LRU;
    }
  },

  /** NonBlockingHashMap (unbounded). */
  NonBlockingHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return new NonBlockingHashMap<K, V>(builder.initialCapacity);
    }
    @Override public Policy policy() {
      return Policy.UNBOUNDED;
    }
  },

  /** Guava cache, with a maximum size. */
  Guava() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheFactory builder) {
      return CacheBuilder.newBuilder()
          .initialCapacity(builder.initialCapacity)
          .concurrencyLevel(builder.concurrencyLevel)
          .maximumSize(builder.maximumCapacity)
          .<K, V>build().asMap();
    }
    @Override public Policy policy() {
      return Policy.LRU_SEGMENTED;
    }
  },

  /** A HashMap with LIRS eviction, guarded by synchronized monitor. */
  Lirs() {
      @Override public <K, V> ConcurrentMap<K, V>  create(CacheFactory builder) {
        Map<K, V> delegate = new LirsMap<K, V>(builder.maximumCapacity);
        return new SynchronizedForwardingConcurrentMap<K, V>(delegate);
      }
      @Override public Policy policy() {
        return Policy.LIRS;
      }
    },

  /** A concurrent HashMap with LIRS eviction. */
  ConcurrentLirs() {
    @Override public <K, V> ConcurrentMap<K, V>  create(CacheFactory builder) {
        return CacheConcurrentLIRS.newInstance(builder.maximumCapacity);
    }
    @Override public Policy policy() {
      return Policy.LIRS_APPROX;
    }
  };

  public enum Policy {
    UNBOUNDED,
    FIFO,
    SECOND_CHANCE,
    LFU,
    LRU,
    LRU_SEGMENTED,
    LIRS,
    LIRS_APPROX
  }

  /** Creates the cache instance. */
  public abstract <K, V> ConcurrentMap<K, V> create(CacheFactory builder);

  public abstract Policy policy();
}
