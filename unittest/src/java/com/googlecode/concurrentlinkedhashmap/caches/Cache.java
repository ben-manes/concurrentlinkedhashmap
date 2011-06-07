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
package com.googlecode.concurrentlinkedhashmap.caches;

import com.google.common.collect.MapMaker;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.caches.BoundedLinkedHashMap.AccessOrder;
import com.googlecode.concurrentlinkedhashmap.caches.ProductionMap.EvictionPolicy;

import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A collection of cache data structures that can be built.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum Cache {

  /** A concurrent linked hash map. */
  ConcurrentLinkedHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new Builder<K, V>()
          .initialCapacity(builder.initialCapacity)
          .concurrencyLevel(builder.concurrencyLevel)
          .maximumWeightedCapacity(builder.maximumCapacity)
          .build();
    }
  },

  /** A concurrent map using a first-in, first-out eviction policy. */
  Concurrent_Fifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new ProductionMap<K, V>(EvictionPolicy.FIFO, builder);
    }
  },

  /**
   * A concurrent map using a second chance first-in, first-out eviction policy.
   */
  Concurrent_SecondChanceFifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new ProductionMap<K, V>(EvictionPolicy.SECOND_CHANCE,  builder);
    }
  },

  /** A concurrent map using an eager lock-based LRU eviction policy. */
  Concurrent_Lru() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new ProductionMap<K, V>(EvictionPolicy.LRU, builder);
    }
  },

  /** LinkedHashMap in FIFO eviction, guarded by read/write lock. */
  LinkedHashMap_Fifo_Lock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      ReadWriteLock lock = new ReentrantReadWriteLock();
      ConcurrentMap<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.FIFO, builder);
      return new LockForwardingConcurrentMap<K, V>(lock.readLock(), lock.writeLock(), delegate);
    }
  },

  /** LinkedHashMap in LRU eviction, guarded by lock. */
  LinkedHashMap_Lru_Lock() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      Lock lock = new ReentrantLock(); // LRU mutates on reads to update access order
      ConcurrentMap<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.LRU, builder);
      return new LockForwardingConcurrentMap<K, V>(lock, lock, delegate);
    }
  },

  /** LinkedHashMap in FIFO eviction, guarded by synchronized monitor. */
  LinkedHashMap_Fifo_Sync() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      ConcurrentMap<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.FIFO, builder);
      return new SynchronizedForwardingConcurrentMap<K, V>(delegate);
    }
  },

  /** LinkedHashMap in LRU eviction, guarded by synchronized monitor. */
  LinkedHashMap_Lru_Sync() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      ConcurrentMap<K, V> delegate = new BoundedLinkedHashMap<K, V>(AccessOrder.LRU, builder);
      return new SynchronizedForwardingConcurrentMap<K, V>(delegate);
    }
  },

  /** ConcurrentHashMap (unbounded). */
  ConcurrentHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new ConcurrentHashMap<K, V>(builder.initialCapacity, 0.75f, builder.concurrencyLevel);
    }
    @Override public boolean isBounded() { return false; }
  },

  /** Ehcache, using FIFO eviction. */
  Ehcache_Fifo() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.FIFO, builder);
    }
  },

  /** Ehcache, using LRU eviction. */
  Ehcache_Lru() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new EhcacheMap<K, V>(MemoryStoreEvictionPolicy.LRU, builder);
    }
  },

  /** NonBlockingHashMap (unbounded). */
  NonBlockingHashMap() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new NonBlockingHashMap<K, V>(builder.initialCapacity);
    }
    @Override public boolean isBounded() { return false; }
  },

  /** MapMaker, with a maximum size. */
  MapMaker() {
    @Override public <K, V> ConcurrentMap<K, V> create(CacheBuilder builder) {
      return new MapMaker()
          .initialCapacity(builder.initialCapacity)
          .concurrencyLevel(builder.concurrencyLevel)
          .maximumSize(builder.maximumCapacity)
          .makeMap();
    }
  };

  /** Creates the cache instance. */
  abstract <K, V> ConcurrentMap<K, V> create(CacheBuilder builder);

  public boolean isBounded() {
    return true;
  }
}
