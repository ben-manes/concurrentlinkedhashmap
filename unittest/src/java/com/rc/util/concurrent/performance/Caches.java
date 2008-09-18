package com.rc.util.concurrent.performance;

import static net.sf.ehcache.Cache.DEFAULT_CACHE_NAME;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import com.rc.util.concurrent.ConcurrentLinkedHashMap;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * A factory for creating caches for use within tests.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class Caches {
    public static enum Cache {
        CONCURRENT_FIFO("A concurrent linked hashmap, using FIFO eviction"),
        CONCURRENT_SECOND_CHANCE("A concurrent linked hashmap, using second-chance FIFO eviction"),
        CONCURRENT_LRU("A concurrent linked hashmap, using LRU eviction"),
        FAST_FIFO("ConcurrentMap and ConcurrentQueue, using FIFO eviction"),
        FAST_FIFO_2C("ConcurrentMap and ConcurrentQueue, using second-chance FIFO eviction"),
        LINKED_FAST_FIFO_2C("ConcurrentMap and ConcurrentQueue and key-value links, using second-chance FIFO eviction"),
        RW_FIFO("LinkedHashMap in FIFO eviction, guarded by read/write lock"),
        LOCK_LRU("LinkedHashMap in LRU eviction, guarded by lock"),
        SYNC_FIFO("LinkedHashMap in FIFO eviction, guarded by synchronized monitor"),
        SYNC_LRU("LinkedHashMap in LRU eviction, guarded by synchronized monitor"),
        UNBOUNDED("ConcurrentMap with no eviction policy, initially sized at the capacity"),
        EHCACHE_FIFO("Ehcache, using FIFO eviction"),
        EHCACHE_LRU("Ehcache, using LRU eviction"),
        
        // for clients command-line usage for caches to test
        ALL("Performs the test on all of the cache types");
        
        private final String help;
        private Cache(String help) {
            this.help = help;
        }
        public String toHelp() {
            return toString() + ": " + help;
        }
    }
    
    private static volatile CacheManager ehcacheManager;
    
    /**
     * Creates the local cache instance.
     * 
     * @param type     The cache type.
     * @param capacity The cache's capacity.
     * @param maxSize  The unbounded, max possible size.
     * @return         A {@link Map} that automatically evicts.
     */
    public static <K, V> Map<K, V> create(Cache type, int capacity, int max, int nThreads) {
        switch (type) {
            case CONCURRENT_FIFO:
                return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.FIFO, max, nThreads);
            case CONCURRENT_SECOND_CHANCE:
                return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.SECOND_CHANCE, max, nThreads);
            case CONCURRENT_LRU:
                return new ConcurrentLinkedHashMap<K, V>(EvictionPolicy.LRU, max, nThreads);
            case FAST_FIFO:
                return new ConcurrentFifoMap<K, V>(capacity);
            case FAST_FIFO_2C:
                return new SecondChanceMap<K, V>(capacity);
            case LINKED_FAST_FIFO_2C:
                return new SecondChanceLinkedMap<K, V>(capacity, nThreads);
            case RW_FIFO:
                return new LockMap<K, V>(false, capacity);
            case LOCK_LRU:
                return new LockMap<K, V>(true, capacity);
            case SYNC_FIFO:
                return Collections.synchronizedMap(new UnsafeMap<K, V>(false, capacity));
            case SYNC_LRU:
                return Collections.synchronizedMap(new UnsafeMap<K, V>(true, capacity));
            case UNBOUNDED:
                return new ConcurrentHashMap<K, V>(capacity, 0.75f, 1000);
            case EHCACHE_FIFO:
                return createEhcacheMap("FIFO", false, capacity);
            case EHCACHE_LRU:
                return createEhcacheMap("LRU", true, capacity);
            default:
                throw new IllegalStateException("Unknown mode: " + type);
        }
    }
    
    /**
     * Creates an {@link Ehcache} from the local cache's settings.
     * 
     * @param accessOrder The eviction policy: true=LRU, false=FIFO.
     */
    private static <K, V> Map<K, V> createEhcacheMap(String name, boolean accessOrder, int capacity) {
        if (ehcacheManager == null) {
            ehcacheManager = new CacheManager();
            ehcacheManager.setName(CachePerformanceTest.class.getSimpleName());
            ehcacheManager.addCache(createEhcache(DEFAULT_CACHE_NAME, false, 5000));
        }
        Ehcache ehcache = createEhcache(name, accessOrder, capacity);
        ehcacheManager.addCache(ehcache);
        return new EhcacheMap<K, V>(ehcache);
    }
    
    private static Ehcache createEhcache(String name, boolean accessOrder, int capacity) {
        MemoryStoreEvictionPolicy policy = (accessOrder ? MemoryStoreEvictionPolicy.LRU : MemoryStoreEvictionPolicy.FIFO);
        return new net.sf.ehcache.Cache(name, capacity, policy, false, null, false, 0, 0, false, 0, null, null, 0, 0);
    }

    /** A non-thread safe bounded map. Operates in LRU or FIFO mode. */
    private static class UnsafeMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int capacity;
        
        /** 
         * @param accessOrder The eviction policy: true=LRU, false=FIFO.
         * @param capacity    The maximum capacity of the map.
         */
        public UnsafeMap(boolean accessOrder, int capacity) {
            super(capacity, 0.75f, accessOrder);
            this.capacity = capacity;
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }
    
    /** A self-evicting map that is protected by a reentrant locks. */
    private static class LockMap<K, V> extends UnsafeMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final Lock readLock;
        private final Lock writeLock;
        
        /** 
         * @param accessOrder FIFO=RW locks, LRU=Single lock
         * @param capacity    The maximum capacity of the map.
         */
        public LockMap(boolean accessOrder, int capacity) {
            super(accessOrder, capacity);
            if (accessOrder) {
                // LRU mutates on reads to update access order
                readLock = writeLock = new ReentrantLock();
            } else {
                ReadWriteLock lock = new ReentrantReadWriteLock();
                readLock = lock.readLock();
                writeLock = lock.writeLock();
            }
        }
        @Override
        public V get(Object key) {
            readLock.lock();
            try {
                return super.get(key);
            } finally {
                readLock.unlock();
            }
        }
        @Override
        public V put(K key, V value) {
            writeLock.lock();
            try {
                return super.put(key, value);
            } finally {
                writeLock.unlock();
            }
        }
        @Override
        public int size() {
            readLock.lock();
            try {
                return super.size();
            } finally {
                readLock.unlock();
            }
        }
        @Override
        public void clear() {
            writeLock.lock();
            try {
                super.clear();
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /** Exposes the cache as a {@link Map}. */
    private static final class EhcacheMap<K, V> extends AbstractMap<K, V> {
        private final Ehcache cache;
        
        public EhcacheMap(Ehcache cache) {
            this.cache = cache;
        }
        @Override
        @SuppressWarnings("unchecked")
        public V get(Object key) {
            Element element = cache.get(key);
            return (element == null) ? null : (V) element.getObjectValue();
        }
        @Override
        public V put(K key, V value) {
            cache.put(new Element(key, value));
            return null;
        }
        @Override
        public int size() {
            return cache.getSize();
        }
        @Override
        public void clear() {
            cache.removeAll();
        }
        @Override
        public Set<Entry<K, V>> entrySet() {
            throw new UnsupportedOperationException();
        }
    }
}
