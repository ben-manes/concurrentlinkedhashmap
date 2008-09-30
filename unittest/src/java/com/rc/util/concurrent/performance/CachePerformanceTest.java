package com.rc.util.concurrent.performance;

import static com.rc.util.concurrent.performance.Caches.create;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import com.rc.util.concurrent.ConcurrentTestHarness;
import com.rc.util.concurrent.performance.Caches.CacheType;

/**
 * This test can be run at the command-line to evaluate the performance of different cache implementations.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class CachePerformanceTest {
    private enum TestType {
        CONCURRENCY("[runs] [cache type] [num threads] [max contention] [iterations] [percent writes] [cache capacity]");

        private final String help;
        private TestType(String help) {
            this.help = help;
        }
        public String toHelp() {
            return toString() + ": " + help;
        }
    }

    private final Map<Integer, Integer> cache;
    private final boolean contention;
    private final List<Integer> keys;
    private final int percentWrite;
    private final int nThreads;

    /**
     * A test that the cache evicts entries under high load.
     *
     * @param mode         The cache eviction mode.
     * @param nThreads     The number of threads to operate concurrently.
     * @param contention   Whether to create the maximum contention.
     * @param iterations   The number of operations to perform per thread.
     * @param percentWrite The percentage of writes (e.g. 20 => 20% writes / 80% reads)
     * @param capacity     The maximum capacity of the cache.
     */
    public CachePerformanceTest(CacheType type, int nThreads, boolean contention, int iterations, int percentWrite, int capacity) {
        if ((iterations < 1) || (percentWrite < 0) || (percentWrite > 100)) {
            throw new IllegalArgumentException("Write percentage out of bounds");
        }
        List<Integer> keys = new ArrayList<Integer>(iterations);
        for (int i=0; i<iterations; i++) {
            keys.add(i);
        }
        this.nThreads = nThreads;
        this.contention = contention;
        this.percentWrite = percentWrite;
        this.keys = Collections.unmodifiableList(keys);
        this.cache = create(type, capacity, iterations, nThreads);
    }

    public Map<Integer, Integer> getCache() {
        return cache;
    }

    /**
     * @return The execution time of the test.
     */
    public long executeLockTest() throws InterruptedException {
        long time = ConcurrentTestHarness.timeTasks(nThreads, new CacheThrasher()).getExecutionTime();
        cache.clear();
        return time;
    }

    /**
     * Executes read or write operations against the cache, thereby testing the efficiency of the
     * concurrency constructs guarding the cache. The worse the lock, the longer the execution time.
     */
    private final class CacheThrasher implements Callable<Long> {
        private final CyclicBarrier barrier = new CyclicBarrier(nThreads);

        public Long call() throws InterruptedException, BrokenBarrierException {
            List<Integer> keys = new ArrayList<Integer>(CachePerformanceTest.this.keys);
            Random random = new Random();
            Collections.shuffle(keys);
            barrier.await();

            for (Integer key : keys) {
                if (percentWrite > random.nextInt(100)) {
                    cache.put(key, key);
                } else {
                    cache.get(key);
                }
                if (contention) {
                    Thread.yield();
                }
            }
            return null;
        }
    }

    /**
     * Forces contention on the cache, thereby determining its performance under various concurrency scenarios.
     */
    private static void doThrashingTest(int runs, CacheType type, int nThreads, boolean contention, int iterations,
                                        int percentWrite, int capacity) throws InterruptedException {
        long sum = 0;
        List<Long> times = new ArrayList<Long>(runs);
        CachePerformanceTest test = new CachePerformanceTest(type, nThreads, contention, iterations, percentWrite, capacity);
        for (int i=1; i<=runs; i++) {
            long time = TimeUnit.NANOSECONDS.toMillis(test.executeLockTest());
            System.out.printf("#%d: %s ms\n", i, DecimalFormat.getInstance().format(time));
            times.add(time);
            sum += time;
            if (test.cache.size() > capacity) {
                System.out.printf("WARNING: Cache exceeds capacity: %d/%d\n", capacity, test.cache.size());
            }
        }
        System.out.printf("Average: %s ms\n", DecimalFormat.getInstance().format(sum/runs));

        // upper, lower 10% removed
        sum = 0;
        int bound = runs / 10;
        Collections.sort(times);
        for (int i=bound; i<(runs-bound); i++) {
            sum += times.get(i);
        }
        System.out.printf("Corrected Average: %s ms\n", DecimalFormat.getInstance().format(sum/(runs-2*bound)));
    }

    /** Prints the command's help message. */
    private static void printHelp() {
        System.out.println("\nCachePerformanceTest [test type] [arguments...]\n");
        System.out.println("Test types:");
        for (TestType test : TestType.values()) {
            System.out.println("\t" + test.toHelp());
        }
        System.out.println("Cache types:");
        for (CacheType type : CacheType.values()) {
            System.out.println("\t" + type.toHelp());
        }
        System.out.println();
    }

    /** Executes the performance test via the command-line. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }
        try {
            switch (TestType.valueOf(args[0].toUpperCase())) {
                case CONCURRENCY:
                    int runs = Integer.valueOf(args[1]);
                    CacheType type = CacheType.valueOf(args[2].toUpperCase());
                    int nThreads = Integer.valueOf(args[3]);
                    boolean contention = Boolean.valueOf(args[4]);
                    int iterations = Integer.valueOf(args[5]);
                    int percentWrite = Integer.valueOf(args[6]);
                    int capacity = Integer.valueOf(args[7]);
                    if (type == CacheType.ALL) {
                        for (CacheType cacheType : CacheType.values()) {
                            if (cacheType != CacheType.ALL) {
                                System.out.println("\n" + cacheType + ":");
                                doThrashingTest(runs, cacheType, nThreads, contention, iterations, percentWrite, capacity);
                            }
                        }
                    } else {
                        doThrashingTest(runs, type, nThreads, contention, iterations, percentWrite, capacity);
                    }
                    break;
            }
        } catch (Exception e) {
            printHelp();
            throw e;
        }
    }
}
