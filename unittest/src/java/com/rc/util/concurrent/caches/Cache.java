package com.rc.util.concurrent.caches;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.rc.util.concurrent.ConcurrentLinkedHashMap;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * A factory for creating caches for use within tests.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public enum Cache {
    CONCURRENT_FIFO() { /** A concurrent linked hashmap, using FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.FIFO, capacity, nThreads);
        }
    },
    CONCURRENT_SECOND_CHANCE() { /** A concurrent linked hashmap, using second-chance FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.SECOND_CHANCE, capacity, nThreads);
        }
    },
    CONCURRENT_LRU() { /** A concurrent linked hashmap, using LRU eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.LRU, capacity, nThreads);
        }
    },
    FAST_FIFO() { /** ConcurrentMap and ConcurrentQueue, using FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new ConcurrentFifoMap<K, V>(capacity);
        }
    },
    FAST_FIFO_2C() { /** ConcurrentMap and ConcurrentQueue, using second-chance FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new SecondChanceMap<K, V>(capacity);
        }
    },
    LINKED_FAST_FIFO_2C() { /** ConcurrentMap and ConcurrentQueue and key-value links, using second-chance FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new SecondChanceLinkedMap<K, V>(capacity, nThreads);
        }
    },
    RW_FIFO() { /** LinkedHashMap in FIFO eviction, guarded by read/write lock */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new LockMap<K, V>(false, capacity);
        }
    },
    LOCK_LRU() { /** LinkedHashMap in LRU eviction, guarded by lock */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new LockMap<K, V>(true, capacity);
        }
    },
    SYNC_FIFO() { /** LinkedHashMap in FIFO eviction, guarded by synchronized monitor */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return Collections.synchronizedMap(new UnsafeMap<K, V>(false, capacity));
        }
    },
    SYNC_LRU() { /** LinkedHashMap in LRU eviction, guarded by synchronized monitor */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return Collections.synchronizedMap(new UnsafeMap<K, V>(true, capacity));
        }
    },
    UNBOUNDED() { /** ConcurrentMap with no eviction policy, initially sized to avoid internal resizing */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new ConcurrentHashMap<K, V>(max, 0.75f, 1000);
        }
    },
    EHCACHE_FIFO() { /** Ehcache, using FIFO eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new EhcacheMap<K, V>(false, capacity);
        }
    },
    EHCACHE_LRU() { /** Ehcache, using LRU eviction */
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return new EhcacheMap<K, V>(true, capacity);
        }
    };
    
    /**
     * Creates the local cache instance.
     *
     * @param type     The cache type.
     * @param capacity The cache's capacity.
     * @param maxSize  The unbounded, max possible size.
     * @return         A cache wrapped under a {@link Map} interface. 
     */
    public abstract <K, V> Map<K, V> create(int capacity, int max, int nThreads);
}
