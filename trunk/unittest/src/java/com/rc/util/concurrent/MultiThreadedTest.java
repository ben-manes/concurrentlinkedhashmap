package com.rc.util.concurrent;

import java.util.EnumSet;
import java.util.Set;

import org.testng.annotations.Test;

import com.rc.util.concurrent.performance.CachePerformanceTest;
import com.rc.util.concurrent.performance.Caches.CacheType;

/**
 * The concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class MultiThreadedTest extends BaseTest {

    /**
     * Tests that the cache is in the correct test after a read-write load.
     */
    @Test(groups="development")
    public void readWrite() throws InterruptedException {
        Set<CacheType> types = EnumSet.of(CacheType.CONCURRENT_FIFO, CacheType.CONCURRENT_SECOND_CHANCE, CacheType.CONCURRENT_LRU);
        for (CacheType type : types) {
            CachePerformanceTest concurrencyTest = new CachePerformanceTest(type, 20, true, 10000, 25, capacity);
            concurrencyTest.executeLockTest();
            validator.state((ConcurrentLinkedHashMap<Integer, Integer>) concurrencyTest.getCache());
        }
    }
}
