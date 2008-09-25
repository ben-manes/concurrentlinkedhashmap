package com.rc.util.concurrent;

import static com.rc.util.concurrent.Validator.validate;
import static com.rc.util.concurrent.Validator.validateEmpty;
import static com.rc.util.concurrent.Validator.validateNodesMarked;
import static com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.createWorkingSet;
import static com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.determineEfficiency;
import static java.lang.String.format;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang.SerializationUtils;
import org.testng.Assert;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionListener;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;
import com.rc.util.concurrent.performance.CachePerformanceTest;
import com.rc.util.concurrent.performance.Caches;
import com.rc.util.concurrent.performance.SecondChanceMap;
import com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.Distribution;
import com.rc.util.concurrent.performance.Caches.Cache;

/**
 * The non-concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public final class ConcurrentLinkedHashMapTest extends Assert {
    private static final EvictionMonitor<Integer, Integer> guard = EvictionMonitor.newGuard();
    private static int size = 5000;
    private boolean exhaustive;

    /**
     * Initializes the test with runtime properties.
     */
    @BeforeClass()
    public void before() {
        this.exhaustive = Boolean.valueOf(System.getProperty("exhaustive"));
        if (exhaustive) {
            System.out.println("Exhaustive testing. This will take a while...");
        }
    }

    /**
     * Tests {@link ConcurrentLinkedHashMap#ConcurrentLinkedHashMap(int)} is empty.
     */
    @Test
    public void empty() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size, guard);
        validate(cache, exhaustive);
        validateEmpty(cache);
    }

    /**
     * Tests {@link Map#putAll(Map)}.
     */
    @Test
    public void putAll() {
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size);
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size, guard);
        cache.putAll(expected);

        validateNodesMarked(cache, false);
        validate(cache, exhaustive);
        assertEquals(cache, expected);
    }

    /**
     * Tests {@link Map#put()}.
     */
    @Test
    public void put() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size);
        cache.put(0, 0);
        int old = cache.put(0, 1);
        int current = cache.get(0);

        assertEquals(old, 0);
        assertEquals(current, 1);

        validate(cache, exhaustive);
    }

    /**
     * Tests {@link Map#putIfAbsent()}.
     */
    @Test
    public void putIfAbsent() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size, guard);
        for (Integer i=0; i<size; i++) {
            assertNull(cache.putIfAbsent(i, i));
            assertEquals(cache.putIfAbsent(i, -1), i);
            assertEquals(cache.data.get(i).getValue(), i);
        }
        assertEquals(cache.size(), size, "Not warmed to max size");
        validate(cache, exhaustive);
        validateNodesMarked(cache, false);
        assertEquals(cache, createWarmedMap(EvictionPolicy.SECOND_CHANCE, size));
    }

    /**
     * Tests {@link Map#containsKey(Object)}, {@link Map#containsValue(Object)}, {@link Map#get(Object)}.
     */
    @Test
    public void retrieval() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, guard);
        for (Integer i=-size; i<0; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        for (Integer i=0; i<size; i++) {
            assertEquals(cache.get(i), i);
            assertTrue(cache.containsKey(i));
            assertTrue(cache.containsValue(i));
        }
        for (Integer i=size; i<size*2; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        validate(cache, exhaustive);
        validateNodesMarked(cache, true);
    }

    /**
     * Tests {@link Map#remove()} and {@link java.util.concurrent.ConcurrentMap#remove(Object, Object)}
     */
    @Test
    public void remove() {
        EvictionMonitor monitor = EvictionMonitor.newMonitor();

        // Map#remove()
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, monitor);
        for (Integer i=0; i<size; i++) {
            assertEquals(cache.remove(i), i, format("Failure on index #%d", i));
            assertNull(cache.remove(i), "Not fully removed");
            assertFalse(cache.containsKey(i));
        }
        validate(cache, exhaustive);
        validateEmpty(cache);
        assertEquals(monitor.evicted.size(), size);

        // ConcurrentMap#remove()
        monitor.evicted.clear();
        cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, monitor);
        for (Integer i=0; i<size; i++) {
            assertFalse(cache.remove(i, -1));
            assertTrue(cache.remove(i, i));
            assertFalse(cache.remove(i, -1));
            assertFalse(cache.containsKey(i));
        }
        validate(cache, exhaustive);
        validateEmpty(cache);
        validateNodesMarked(cache, false);
        assertEquals(monitor.evicted.size(), size);
    }

    /**
     * Tests {@link java.util.concurrent.ConcurrentMap#replace(Object, Object)} and {@link java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)}.
     */
    @Test
    public void replace() {
        Integer dummy = -1;
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size);
        for (Integer i=0; i<size; i++) {
            assertNotNull(cache.replace(i, dummy));
            assertFalse(cache.replace(i, i, i));
            assertEquals(cache.data.get(i).getValue(), dummy);
            assertTrue(cache.replace(i, dummy, i));
            assertEquals(cache.remove(i), i);
            assertNull(cache.replace(i, i));
        }
        validate(cache, exhaustive);
        validateEmpty(cache);
        validateNodesMarked(cache, false);
    }

    /**
     * Tests {@link Map#clear()}.
     */
    @Test
    public void clear() {
        EvictionMonitor monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, monitor);
        cache.clear();
        validate(cache, exhaustive);
        assertEquals(monitor.evicted.size(), size);
    }

    /**
     * Tests {@link ConcurrentLinkedHashMap#setCapacity(int)}.
     */
    @Test
    public void capacity() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size);

        int capacity = 2*size;
        cache.setCapacity(capacity);
        assertEquals(cache.capacity(), capacity);
        assertEquals(cache, createWarmedMap(EvictionPolicy.SECOND_CHANCE, size));
        validate(cache, exhaustive);

        capacity = size/2;
        cache.setCapacity(capacity);
        assertEquals(cache.capacity(), capacity);
        assertEquals(cache.size(), capacity);
        validate(cache, exhaustive);

        capacity = 1;
        cache.setCapacity(capacity);
        assertEquals(cache.capacity(), capacity);
        assertEquals(cache.size(), capacity);
        validate(cache, exhaustive);

        try {
            cache.setCapacity(-1);
            fail("Capacity must be positive");
        } catch (Exception e) {
            assertEquals(cache.capacity(), capacity);
        }
    }

    /**
     * Tests that entries are evicted in FIFO order.
     */
    @Test
    public void evictAsFifo() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.FIFO, size, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Tests that entries are evicted in FIFO order under a SECOND_CHANCE policy where none are saved.
     */
    @Test
    public void evictSecondChanceAsFifo() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Executes a FIFO eviction test.
     */
    private void doFifoEvictionTest(ConcurrentLinkedHashMap<Integer, Integer> cache, EvictionMonitor<Integer, Integer> monitor) {
        for (Integer i=0; i<3*size; i++) {
            cache.put(i, i);
        }

        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(size);
        for (Integer i=2*size; i<3*size; i++) {
            expected.put(i, i);
        }

        validate(cache, exhaustive);
        validateNodesMarked(cache, false);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), 2*size);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order using a simple working set.
     */
    @Test
    public void evictAsSecondChanceFifoSimple() {
        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(size);
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size, monitor);
        for (Integer i=0; i<size; i++) {
            cache.put(i, i);
            if (i%2 == 0) {
                cache.get(i);
                expected.put(i, i);
                assertTrue(cache.data.get(i).isMarked());
            }
        }

        for (Integer i=size; i<(size+size/2); i++) {
            cache.put(i, i);
            expected.put(i, i);
        }

        validate(cache, exhaustive);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), size/2);
    }

    /**
     * Tests that entries are evicted in FIFO order using a complex working set.
     */
    @Test
    public void efficencyTestAsFifo() {
        ConcurrentLinkedHashMap<Integer, Integer> actual = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.FIFO, size);
        Map<Integer, Integer> expected = Caches.create(Cache.SYNC_FIFO, size, size, 1);
        doEfficencyTest(actual, expected, true);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order using a complex working set.
     */
    @Test
    public void efficencyTestAsSecondChanceFifo() {
        ConcurrentLinkedHashMap<Integer, Integer> actual = new ConcurrentLinkedHashMap<Integer, Integer>(EvictionPolicy.SECOND_CHANCE, size);
        Map<Integer, Integer> expected = new SecondChanceMap<Integer, Integer>(size);
        doEfficencyTest(actual, expected, true);
    }

    /**
     * Executes a complex eviction test.
     */
    private void doEfficencyTest(ConcurrentLinkedHashMap<Integer, Integer> actual, Map<Integer, Integer> expected, boolean strict) {
        List<Integer> workingSet = createWorkingSet(Distribution.EXPONENTIAL, 10*size, 10*size);
        long hitExpected = determineEfficiency(expected, workingSet);
        long hitActual = determineEfficiency(actual, workingSet);
        if (strict) {
            assertEquals(hitActual, hitExpected);
        }
        assertTrue(hitExpected > 0);
        assertTrue(hitActual > 0);
        validate(actual, exhaustive);
    }

    /**
     * Tests that entries are evicted in LRU order using a complex working set.
     *
     * This cannot be directly compared to a {@link java.util.LinkedHashMap} due to dead nodes on the list.
     */
    @Test
    public void evictAsLru() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.LRU, 10);
        assertTrue(cache.keySet().containsAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        //System.out.println("Initial");
        //printLinkedList(cache);

        // re-order
        cache.get(0);
        cache.get(1);
        cache.get(2);

        assertTrue(cache.keySet().containsAll(Arrays.asList(3, 4, 5, 6, 7, 8, 9, 0, 1, 2)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        //System.out.println("Reordered #1");
        //printLinkedList(cache);

        // evict 3, 4, 5
        cache.put(10, 10);
        cache.put(11, 11);
        cache.put(12, 12);

        assertTrue(cache.keySet().containsAll(Arrays.asList(6, 7, 8, 9, 0, 1, 2, 10, 11, 12)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        //System.out.println("Evict #1");
        //printLinkedList(cache);

        // re-order
        cache.get(6);
        cache.get(7);
        cache.get(8);

        assertTrue(cache.keySet().containsAll(Arrays.asList(9, 0, 1, 2, 10, 11, 12, 6, 7, 8)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        //System.out.println("Reordered #2");
        //printLinkedList(cache);

        // evict 9, 0, 1
        cache.put(13, 13);
        cache.put(14, 14);
        cache.put(15, 15);

        assertTrue(cache.keySet().containsAll(Arrays.asList(2, 10, 11, 12, 6, 7, 8, 13, 14, 15)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        //System.out.println("Evict #2");
        //printLinkedList(cache);
    }

    /**
     * Tests that a full scan was required to evict an entry.
     */
    @Test
    public void evictSecondChanceFullScan() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, monitor);
        for (int i=0; i<size; i++) {
            cache.get(i);
        }
        validateNodesMarked(cache, true);
        assertEquals(cache.size(), size);

        cache.put(size, size);
        assertEquals(cache.size(), size);
        validateNodesMarked(cache, false);
        assertEquals(monitor.evicted.size(), 1);
    }

    /**
     * Tests {@link Object#equals(Object)}, {@link Object#hashCode()}, {@link Object#toString()}.
     */
    @Test
    public void object() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, guard);
        Map<Integer, Integer> expected = new ConcurrentHashMap<Integer, Integer>(size);
        for (Integer i=0; i<size; i++) {
            expected.put(i, i);
        }
        assertEquals(cache, expected);
        assertEquals(cache.hashCode(), expected.hashCode());
        assertEquals(cache.toString(), expected.toString());
    }

    /**
     * Tests serialization.
     */
    @Test
    public void serialize() {
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap(EvictionPolicy.SECOND_CHANCE, size, guard);
        Object cache = SerializationUtils.clone(expected);
        assertEquals(cache, expected);
        validate((ConcurrentLinkedHashMap<Integer, Integer>) cache, exhaustive);
    }

    /**
     * Tests concurrency.
     */
    @Test
    public void concurrency() throws InterruptedException {
        CachePerformanceTest concurrencyTest = new CachePerformanceTest(Cache.CONCURRENT_SECOND_CHANCE, 20, true, 10000, 25, 5000);
        concurrencyTest.executeLockTest();
        validate((ConcurrentLinkedHashMap<Integer, Integer>) concurrencyTest.getCache(), exhaustive);
    }

    /**
     * Creates a map warmed to the specified maximum size.
     */
    private ConcurrentLinkedHashMap<Integer, Integer> createWarmedMap(EvictionPolicy policy, int size, EvictionListener<Integer, Integer>... listeners) {
        ConcurrentLinkedHashMap<Integer, Integer> cache = new ConcurrentLinkedHashMap<Integer, Integer>(policy, size, listeners);
        for (Integer i=0; i<size; i++) {
            assertNull(cache.put(i, i));
            assertEquals(cache.data.get(i).getValue(), i);
        }
        validateNodesMarked(cache, false);
        assertEquals(cache.size(), size, "Not warmed to max size");
        return cache;
    }

    private static final class EvictionMonitor<K, V> implements EvictionListener<K, V>, Serializable {
        private static final long serialVersionUID = 1L;
        final Collection<Entry> evicted;
        final boolean isAllowed;

        private EvictionMonitor(boolean isAllowed) {
            this.isAllowed = isAllowed;
            this.evicted = new ConcurrentLinkedQueue<Entry>();
        }
        public static <K, V> EvictionMonitor<K, V> newMonitor() {
            return new EvictionMonitor<K, V>(true);
        }
        public static <K, V> EvictionMonitor<K, V> newGuard() {
            return new EvictionMonitor<K, V>(false);
        }
        public void onEviction(K key, V value) {
            if (!isAllowed) {
                throw new IllegalStateException("Eviction should not have occured");
            }
            evicted.add(new Entry(key, value));
        }
        final class Entry {
            K key;
            V value;

            public Entry(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }
    }

    /**
     * Executes the test from the command-line, with the specified maximum capacity.
     *
     * @param args[0] The maximum capacity of the map.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("ConcurrentLinkedHashMapTest [capacity]");
            return;
        }
        ConcurrentLinkedHashMapTest.size = Integer.valueOf(args[0]);
        if ((size < 1) || (3*size < size)) {
            System.out.println("Invalid capacity specified: 1 <= capacity <= (2^31-1)/3");
            return;
        }

        TestNG tester = new TestNG();
        tester.setTestClasses(new Class[] {ConcurrentLinkedHashMapTest.class});
        tester.addListener(new TestListenerAdapter());
        tester.run();
    }
}
