package com.rc.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * The concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class MultiThreadedTest extends BaseTest {
    private List<Integer> keys;
    private int nThreads;

    @BeforeClass(alwaysRun=true)
    public void beforeMultiThreaded() {
        int iterations = Integer.valueOf(System.getProperty("performance.iterations"));
        nThreads = Integer.valueOf(System.getProperty("performance.nThreads"));
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
        List<List<Integer>> sets = shuffle(nThreads, keys);
        for (EvictionPolicy policy : EvictionPolicy.values()) {
            ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(policy, capacity, nThreads);
            ConcurrentTestHarness.timeTasks(nThreads, new Thrasher(cache, sets));
            validator.state(cache);
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
        private static final int OPERATIONS = 17;

        private final ConcurrentMap<Integer, Integer> cache;
        private final List<List<Integer>> sets;
        private final AtomicInteger index;

        public Thrasher(ConcurrentMap<Integer, Integer> cache, List<List<Integer>> sets) {
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
                    info("Failed on operation: %d", key%OPERATIONS);
                    throw e;
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
                    Iterator<Integer> i = cache.values().iterator();
                    while (i.hasNext()) {
                        if (i.next() == null) {
                            throw new IllegalArgumentException("Null value");
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
                    cache.equals(new Object());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
