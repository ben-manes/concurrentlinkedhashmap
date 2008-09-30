package com.rc.util.concurrent;

import java.util.EnumSet;
import java.util.Set;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.rc.util.concurrent.caches.Cache;
import com.rc.util.concurrent.performance.CachePerformanceTest;

/**
 * The concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public final class MultiThreadedTest extends BaseTest {
    private int runs;
    private int nThreads;
    private int percentWrite;
    private boolean contention;
    private Set<Integer> keys;
    private Set<Cache> types;
    
    @BeforeClass(groups="performance")
    public void beforeMultiThreaded() {
        runs = Integer.valueOf(System.getProperty("performance.runs"));
        nThreads = Integer.valueOf(System.getProperty("performance.threads.num"));
        percentWrite = Integer.valueOf(System.getProperty("performance.percentWrite"));
        contention = Boolean.valueOf(System.getProperty("performance.threads.forceContention"));
        
        String cacheType = System.getProperty("performance.cache").toUpperCase();
        types = "ALL".equals(cacheType) ? EnumSet.allOf(Cache.class)
                                        : EnumSet.of(Cache.valueOf(cacheType));        
        
        int iterations = Integer.valueOf(System.getProperty("performance.iterations"));
        keys = createWarmedMap(defaultPolicy, iterations).keySet();
    }

    /**
     * Tests that the cache is in the correct test after a read-write load.
     */
    @Test(groups="development")
    public void readWrite() throws InterruptedException {
        Set<Cache> types = EnumSet.of(Cache.CONCURRENT_FIFO, Cache.CONCURRENT_SECOND_CHANCE, Cache.CONCURRENT_LRU);
        for (Cache type : types) {
            CachePerformanceTest concurrencyTest = new CachePerformanceTest(type, 20, true, 10000, 25, capacity);
            concurrencyTest.executeLockTest();
            validator.state((ConcurrentLinkedHashMap<Integer, Integer>) concurrencyTest.getCache());
        }
    }
}
