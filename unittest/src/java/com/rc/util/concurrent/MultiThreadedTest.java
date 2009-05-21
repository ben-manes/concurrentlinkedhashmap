package com.rc.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.rc.util.concurrent.ConcurrentLinkedHashMap;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * The concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class MultiThreadedTest extends BaseTest {
    private Queue<String> failures;
    private List<Integer> keys;
    private int timeout = 180;
    private int nThreads;

    @BeforeClass(alwaysRun=true)
    public void beforeMultiThreaded() {
        int iterations = Integer.valueOf(System.getProperty("concurrent.iterations"));
        nThreads = Integer.valueOf(System.getProperty("concurrent.nThreads"));
        timeout = Integer.valueOf(System.getProperty("concurrent.timeout"));
        failures = new ConcurrentLinkedQueue<String>();
        keys = new ArrayList<Integer>();
        Random random = new Random();
        for (int i=0; i<iterations; i++) {
            keys.add(random.nextInt(iterations/100));
        }
    }

    /**
     * Tests that the cache is in the correct test after a read-write load.
     */
    @Test(groups="development")
    public void concurrent() throws InterruptedException {
        debug("concurrent: START");
        final List<List<Integer>> sets = shuffle(nThreads, keys);
        ThreadPoolExecutor es = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        for (final EvictionPolicy policy : EvictionPolicy.values()) {
            debug("Testing with policy: %s", policy);
            final ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(policy, capacity, nThreads);
            Future<?> future = es.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    ConcurrentTestHarness.timeTasks(nThreads, new Thrasher(cache, sets));
                    return null;
                }
            });
            try {
                future.get(timeout, TimeUnit.SECONDS);
                validator.state(cache);
            } catch (ExecutionException e) {
                fail("Exception during test: " + e.toString(), e);
            } catch (TimeoutException e) {
                for (StackTraceElement[] trace : Thread.getAllStackTraces().values()) {
                    for (StackTraceElement element : trace) {
                        System.out.println("\tat " + element);
                    }
                    if (trace.length > 0) {
                        System.out.println("------");
                    }
                }
                es.shutdownNow();
                while (es.getPoolSize() > 0) { /* spin until terminated */ }

                // Print the state of the cache
                debug("Cached Elements: %s", cache.toString());
                debug("List Forward:\n%s", validator.printFwd(cache));
                debug("List Backward:\n%s", validator.printBwd(cache));

                // Print the recorded failures
                for (String failure : failures) {
                    debug(failure);
                }
                fail("Spun forever");
            }
        }
        debug("concurrent: END");
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
     * Executes operations against the cache to simulate random load.
     */
    private final class Thrasher implements Runnable {
        private static final int OPERATIONS = 16;

        private final ConcurrentLinkedHashMap<Integer, Integer> cache;
        private final List<List<Integer>> sets;
        private final AtomicInteger index;

        public Thrasher(ConcurrentLinkedHashMap<Integer, Integer> cache, List<List<Integer>> sets) {
            this.index = new AtomicInteger();
            this.cache = cache;
            this.sets = sets;
        }

        public void run() {
            int id = index.getAndIncrement();
            debug("#%d: STARTING", id);
            for (Integer key : sets.get(id)) {
                try {
                    execute(key);
                } catch (RuntimeException e) {
                    String error = String.format("Failed: key %s on operation %d for node %s", key, key%OPERATIONS, validator.printNode(key, cache));
                    failures.add(error);
                    throw e;
                } catch (Throwable thr) {
                    String error = String.format("Halted: key %s on operation %d for node %s", key, key%OPERATIONS, validator.printNode(key, cache));
                    failures.add(error);
                }
            }
            debug("#%d: ENDING", id);
        }

        private void execute(int key) {
            switch (key % OPERATIONS) {
                case 0:
                    cache.containsKey(key);
                    break;
                case 1:
                    cache.containsValue(key);
                    break;
                case 2:
                    cache.isEmpty();
                    break;
                case 3:
                    cache.size();
                    break;
                case 4:
                    cache.get(key);
                    break;
                case 5:
                    cache.put(key, key);
                    break;
                case 6:
                    cache.putIfAbsent(key, key);
                    break;
                case 7:
                    cache.remove(key);
                    break;
                case 8:
                    cache.remove(key, key);
                    break;
                case 9:
                    cache.replace(key, key);
                    break;
                case 10:
                    cache.replace(key, key, key);
                    break;
                case 11:
                    cache.clear();
                    break;
                case 12:
                    for (Integer i : cache.keySet()) {
                        if (i == null) {
                            throw new IllegalArgumentException();
                        }
                    }
                    break;
                case 13:
                    for (Integer i : cache.values()) {
                        if (i == null) {
                            throw new IllegalArgumentException();
                        }
                    }
                    break;
                case 14:
                    for (Entry<Integer, Integer> entry : cache.entrySet()) {
                        if ((entry == null) || (entry.getKey() == null) || (entry.getValue() == null)) {
                            throw new IllegalArgumentException(String.valueOf(entry));
                        }
                    }
                    break;
                case 15:
                    cache.hashCode();
                    break;
                case 16:
                    cache.equals(cache);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
