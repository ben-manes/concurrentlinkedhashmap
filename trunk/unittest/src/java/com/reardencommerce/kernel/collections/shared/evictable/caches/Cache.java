package com.reardencommerce.kernel.collections.shared.evictable.caches;

import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.FIFO;
import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.LRU;
import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.SECOND_CHANCE;
import static java.util.Collections.synchronizedMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap;

/**
 * A factory for creating caches for use within tests.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public enum Cache {
    CONCURRENT_FIFO() { /** A concurrent linked hashmap, using FIFO eviction */
        @Override
        public <K, V> Map<K, V> create(int capacity, int max, int nThreads) {
            return ConcurrentLinkedHashMap.create(FIFO, capacity, nThreads);
        }
    },
    CONCURRENT_SECOND_CHANCE() { /** A concurrent linked hashmap, using second-chance FIFO eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return ConcurrentLinkedHashMap.create(SECOND_CHANCE, capacity, nThreads);
        }
    },
    CONCURRENT_LRU() { /** A concurrent linked hashmap, using LRU eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return ConcurrentLinkedHashMap.create(LRU, capacity, nThreads);
        }
    },
    FAST_FIFO() { /** ConcurrentMap and ConcurrentQueue, using FIFO eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new ConcurrentFifoMap<K, V>(capacity);
        }
    },
    FAST_FIFO_2C() { /** ConcurrentMap and ConcurrentQueue, using second-chance FIFO eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new SecondChanceMap<K, V>(capacity);
        }
    },
    FAST_FIFO_LINKED_2C() { /** ConcurrentMap and ConcurrentQueue and key-value links, using second-chance FIFO eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new SecondChanceLinkedMap<K, V>(capacity, nThreads);
        }
    },
    RW_FIFO() { /** LinkedHashMap in FIFO eviction, guarded by read/write lock */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new LockMap<K, V>(false, capacity);
        }
    },
    LOCK_LRU() { /** LinkedHashMap in LRU eviction, guarded by lock */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new LockMap<K, V>(true, capacity);
        }
    },
    SYNC_FIFO() { /** LinkedHashMap in FIFO eviction, guarded by synchronized monitor */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return synchronizedMap(new UnsafeMap<K, V>(false, capacity));
        }
    },
    SYNC_LRU() { /** LinkedHashMap in LRU eviction, guarded by synchronized monitor */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return synchronizedMap(new UnsafeMap<K, V>(true, capacity));
        }
    },
    UNBOUNDED() { /** ConcurrentMap with no eviction policy, initially sized to avoid internal resizing */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new ConcurrentHashMap<K, V>(max, 0.75f, 1000);
        }
    },
    EHCACHE_FIFO() { /** Ehcache, using FIFO eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new EhcacheMap<K, V>(false, capacity);
        }
    },
    EHCACHE_LRU() { /** Ehcache, using LRU eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return new EhcacheMap<K, V>(true, capacity);
        }
    };

    /**
     * Creates the local cache instance.
     *
     * @param capacity The cache's capacity.
     * @param maxSize  The unbounded, max possible size.
     * @param nThreads The max number of concurrency accesses.
     * @return         A cache provided under a {@link Map} interface.
     */
    public abstract <K, V> Map<K, V>  create(int capacity, int max, int nThreads);
}
