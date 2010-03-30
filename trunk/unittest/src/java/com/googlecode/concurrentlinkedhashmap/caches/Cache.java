package com.googlecode.concurrentlinkedhashmap.caches;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import static java.util.Collections.synchronizedMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory for creating caches for use within tests.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public enum Cache {
    CONCURRENT_LRU() { /** A concurrent linked hashmap, using LRU eviction */
        @Override
        public <K, V> Map<K, V>  create(int capacity, int max, int nThreads) {
            return ConcurrentLinkedHashMap.<K, V>builder()
                .maximumCapacity(capacity)
                .concurrencyLevel(nThreads)
                .build();
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
     * @param max      The unbounded, max possible size.
     * @param nThreads The max number of concurrency accesses.
     * @return         A cache provided under a {@link Map} interface.
     */
    public abstract <K, V> Map<K, V>  create(int capacity, int max, int nThreads);
}
