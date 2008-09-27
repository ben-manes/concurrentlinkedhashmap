package com.rc.util.concurrent;

import java.util.EnumSet;
import java.util.Set;

import org.testng.annotations.Test;

import com.rc.util.concurrent.performance.CachePerformanceTest;
import com.rc.util.concurrent.performance.Caches.Cache;

/**
 * The concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class MultiThreadedTest extends BaseTest {

    /**
     * Tests that the cache is in the correct test after a read-write load.
     */
    @Test
    public void readWrite() throws InterruptedException {
        Set<Cache> types = EnumSet.of(Cache.CONCURRENT_FIFO, Cache.CONCURRENT_SECOND_CHANCE, Cache.CONCURRENT_LRU);
        for (Cache type : types) {
            CachePerformanceTest concurrencyTest = new CachePerformanceTest(type, 20, true, 10000, 25, capacity);
            concurrencyTest.executeLockTest();
            validator.state((ConcurrentLinkedHashMap<Integer, Integer>) concurrencyTest.getCache());
        }
    }
}
