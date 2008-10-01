package com.rc.util.concurrent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.rc.util.concurrent.caches.Cache;

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

    @BeforeClass(alwaysRun=true)
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
        List<List<Integer>> sets = shuffle(nThreads, keys);
        for (Cache type : types) {
            ConcurrentLinkedHashMap<Integer, Integer> cache = type.create(capacity, capacity, nThreads);
            execute(cache, new Thrasher<Integer>(cache, sets));
            validator.state(cache);
        }
    }

    /**
     * Compares the concurrency performance of the cache implementations
     */
    @Test(groups="performance")
    public void concurrency() throws InterruptedException {
        List<List<Integer>> sets = shuffle(nThreads, keys);
        Map<Cache, String> averages = new EnumMap<Cache, String>(Cache.class);
        for (Cache type : types) {
            long sum = 0;
            info("Cache Type: %s:", type);
            List<Long> times = new ArrayList<Long>(runs);
            for (int i=0; i<runs; i++) {
                Map<Integer, Integer> cache = type.create(capacity, capacity, nThreads);
                long time = TimeUnit.NANOSECONDS.toMillis(execute(cache, new Thrasher<Integer>(cache, sets)));
                info("#%d: %s ms", i, time);
                times.add(time);
                sum += time;
            }
            info("Average: %s ms", DecimalFormat.getInstance().format(sum/runs));

            // upper, lower 10% removed
            sum = 0;
            int bound = runs / 10;
            Collections.sort(times);
            for (int i=bound; i<(runs-bound); i++) {
                sum += times.get(i);
            }
            String average = DecimalFormat.getInstance().format(sum/(runs-2*bound));
            averages.put(type, average);
            info("Corrected Average: %s ms\n", average);
        }
        
        info("Comparisions:");
        for (Entry<Cache, String> entry : averages.entrySet()) {
            StringBuilder buffer = new StringBuilder(60);
            String cacheName = entry.getKey().toString();
            buffer.append("\t - ")
                  .append(cacheName)
                  .append(':');
            int spaces = 25 - cacheName.length();
            for (int j=0; j<spaces; j++) {
                buffer.append(' ');
            }
            buffer.append(entry.getValue())
                  .append(" ms");
            info(buffer.toString());
        }
    }

    /**
     * @return The execution time of the test run.
     */
    public long execute(Map<?, ?> cache, Runnable runner) throws InterruptedException {
        long time = ConcurrentTestHarness.timeTasks(nThreads, runner);
        cache.clear();
        return time;
    }

    /**
     * Based on the passed in working set, creates N shuffled variants.
     *
     * @param samples    The number of variants to create.
     * @param workingSet The base working set to build from.
     */
    private <T> List<List<T>> shuffle(int samples, Collection<T> workingSet) {
        List<List<T>> sets = new ArrayList<List<T>>(samples);
        for (int i=0; i<samples; i++) {
            List<T> set = new ArrayList<T>(workingSet);
            Collections.shuffle(set);
            sets.add(set);
        }
        return sets;
    }

    /**
     * Executes read or write operations against the cache, thereby testing the efficiency of the
     * concurrency constructs guarding the cache. The worse the lock, the longer the execution time.
     */
    private final class Thrasher<T> implements Runnable {
        private final Map<T, T> cache;
        private final List<List<T>> sets;
        private final AtomicInteger index;

        public Thrasher(Map<T, T> cache, List<List<T>> sets) {
            this.index = new AtomicInteger();
            this.cache = cache;
            this.sets = sets;
        }

        public void run() {
            int id = index.getAndIncrement();
            Random random = new Random();
            for (T key : sets.get(id)) {
                if (percentWrite > random.nextInt(100)) {
                    cache.put(key, key);
                } else {
                    cache.get(key);
                }
                if (contention) {
                    Thread.yield();
                }
            }
        }
    }
}
