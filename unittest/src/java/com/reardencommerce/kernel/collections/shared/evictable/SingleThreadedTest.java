package com.reardencommerce.kernel.collections.shared.evictable;

import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.create;
import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.FIFO;
import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.LRU;
import static com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy.SECOND_CHANCE;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.Test;

import com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.Node;

/**
 * The non-concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public final class SingleThreadedTest extends BaseTest {

    public SingleThreadedTest() {
        super(Integer.valueOf(System.getProperty("singleThreaded.maximumCapacity")));
    }

    /**
     * Tests {@link ConcurrentLinkedHashMap#ConcurrentLinkedHashMap(int)} is empty.
     */
    @Test(groups="development")
    public void empty() {
        debug(" * empty: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        validator.state(cache);
        validator.empty(cache);
    }

    /**
     * Tests {@link Map#putAll(Map)}.
     */
    @Test(groups="development")
    public void putAll() {
        debug(" * putAll: START");
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        cache.putAll(expected);

        validator.allNodesMarked(cache, false);
        validator.state(cache);
        assertEquals(cache, expected);
    }

    /**
     * Tests {@link Map#put()}.
     */
    @Test(groups="development")
    public void put() {
        debug(" * put: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(defaultPolicy, capacity);
        cache.put(0, 0);
        int old = cache.put(0, 1);
        int current = cache.get(0);

        assertEquals(old, 0);
        assertEquals(current, 1);

        validator.state(cache);
    }

    /**
     * Tests {@link Map#putIfAbsent()}.
     */
    @Test(groups="development")
    public void putIfAbsent() {
        debug(" * putIfAbsent: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        for (Integer i=0; i<capacity; i++) {
            assertNull(cache.putIfAbsent(i, i));
            assertEquals(cache.putIfAbsent(i, -1), i);
            assertEquals(cache.data.get(i).getValue(), i);
        }
        assertEquals(cache.size(), capacity, "Not warmed to max size");
        validator.state(cache);
        validator.allNodesMarked(cache, (defaultPolicy == SECOND_CHANCE));
        assertEquals(cache, createWarmedMap());
    }

    /**
     * Tests {@link Map#containsKey(Object)}, {@link Map#containsValue(Object)}, {@link Map#get(Object)}.
     */
    @Test(groups="development")
    public void retrieval() {
        debug(" * retrieval: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        for (Integer i=-capacity; i<0; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        for (Integer i=0; i<capacity; i++) {
            assertEquals(cache.get(i), i);
            assertTrue(cache.containsKey(i));
            assertTrue(cache.containsValue(i));
        }
        for (Integer i=capacity; i<capacity*2; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        validator.state(cache);
        validator.allNodesMarked(cache, true);
    }

    /**
     * Tests that the Fifo policy is working correctly when the entry is retrieved by {@link Map#containsKey(Object)}
     * and {@link Map#putIfAbsent()}. The latter is critical for proper usage in a memoizer (e.g. SelfPopulatingMap).
     */
    @Test(groups="development")
    public void fifoOnAccess() {
        debug(" * fifoOnAccess: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(FIFO, 50);
        Node<Integer, Integer> node = cache.sentinel.getNext();
        int length = cache.length.get();
        int size = cache.data.size();
        assertFalse(node.isMarked());

        // Get
        cache.get(node.getKey());
        assertSame(cache.sentinel.getNext(), node);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertFalse(node.isMarked());

        // PutIfAbsent
        cache.putIfAbsent(node.getKey(), node.getValue());
        assertSame(cache.sentinel.getNext(), node);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertFalse(node.isMarked());
    }

    /**
     * Tests that the SecondChance Fifo policy is working correctly when the entry is retrieved by
     * {@link Map#containsKey(Object)} and {@link Map#putIfAbsent()}. The latter is critical for proper
     * usage in a memoizer (e.g. SelfPopulatingMap).
     */
    @Test(groups="development")
    public void SecondChanceOnAccess() {
        debug(" * secondChanceOnAccess: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(SECOND_CHANCE, 50);
        Node<Integer, Integer> headNode = cache.sentinel.getNext();
        Node<Integer, Integer> tailNode = cache.sentinel.getPrev();
        int length = cache.length.get();
        int size = cache.data.size();
        assertFalse(headNode.isMarked());
        assertFalse(tailNode.isMarked());

        // Get
        cache.get(headNode.getKey());
        assertSame(cache.sentinel.getNext(), headNode);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertTrue(headNode.isMarked());

        // PutIfAbsent
        cache.putIfAbsent(tailNode.getKey(), tailNode.getValue());
        assertSame(cache.sentinel.getPrev(), tailNode);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertTrue(tailNode.isMarked());
    }

    /**
     * Tests the the Lru policy is working correctly when the entry is retrieved by {@link Map#containsKey(Object)}
     * and {@link Map#putIfAbsent()}. The latter is critical for proper usage in a memoizer (e.g. SelfPopulatingMap).
     */
    @Test(groups="development")
    public void lruOnAccess() {
        debug(" * lruOnAccess: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(LRU, 50);
        Node<Integer, Integer> headNode = cache.sentinel.getNext();
        Node<Integer, Integer> tailNode = cache.sentinel.getPrev();
        int length = cache.length.get();
        int size = cache.data.size();
        assertFalse(headNode.isMarked());
        assertFalse(tailNode.isMarked());

        // Get
        cache.get(headNode.getKey());
        assertNotSame(cache.sentinel.getNext(), headNode);
        assertSame(cache.sentinel.getPrev(), headNode);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertFalse(headNode.isMarked());

        // PutIfAbsent
        assertNotSame(cache.sentinel.getPrev(), tailNode); // due to get()
        cache.putIfAbsent(tailNode.getKey(), tailNode.getValue());
        assertSame(cache.sentinel.getPrev(), tailNode);
        assertEquals(cache.length.get(), length);
        assertEquals(cache.data.size(), size);
        assertFalse(tailNode.isMarked());
    }

    /**
     * Tests {@link Map#remove()} and {@link java.util.concurrent.ConcurrentMap#remove(Object, Object)}
     */
    @Test(groups="development")
    public void remove() {
        debug(" * remove: START");
        EvictionMonitor guard = EvictionMonitor.newGuard();

        // Map#remove()
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        for (Integer i=0; i<capacity; i++) {
            assertEquals(cache.remove(i), i, format("Failure on index #%d", i));
            assertNull(cache.remove(i), "Not fully removed");
            assertFalse(cache.containsKey(i));
        }
        validator.state(cache);
        validator.empty(cache);

        // ConcurrentMap#remove()
        cache = createWarmedMap(guard);
        for (Integer i=0; i<capacity; i++) {
            assertFalse(cache.remove(i, -1));
            assertTrue(cache.remove(i, i));
            assertFalse(cache.remove(i, -1));
            assertFalse(cache.containsKey(i));
        }
        validator.state(cache);
        validator.empty(cache);
        validator.allNodesMarked(cache, false);
    }

    /**
     * Tests {@link java.util.concurrent.ConcurrentMap#replace(Object, Object)} and {@link java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)}.
     */
    @Test(groups="development")
    public void replace() {
        debug(" * replace: START");
        Integer dummy = -1;
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
        for (Integer i=0; i<capacity; i++) {
            assertNotNull(cache.replace(i, dummy));
            assertFalse(cache.replace(i, i, i));
            assertEquals(cache.data.get(i).getValue(), dummy);
            assertTrue(cache.replace(i, dummy, i));
            assertEquals(cache.remove(i), i);
            assertNull(cache.replace(i, i));
        }
        validator.state(cache);
        validator.empty(cache);
        validator.allNodesMarked(cache, false);
    }

    /**
     * Tests {@link Map#clear()}.
     */
    @Test(groups="development")
    public void clear() {
        debug(" * clear: START");
        EvictionMonitor guard = EvictionMonitor.newGuard();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        cache.clear();
        validator.state(cache);
    }

    /**
     * Tests {@link ConcurrentLinkedHashMap#setCapacity(int)}.
     */
    @Test(groups="development")
    public void capacity() {
        debug(" * capacity: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

        int newMaxCapacity = 2*capacity;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache, createWarmedMap());
        validator.state(cache);
        debug("capacity: #1 done");

        newMaxCapacity = capacity/2;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);
        debug("capacity: #2 done");

        newMaxCapacity = 1;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);
        debug("capacity: #3 done");

        try {
            cache.setCapacity(-1);
            fail("Capacity must be positive");
        } catch (Exception e) {
            assertEquals(cache.capacity(), newMaxCapacity);
        }
    }

    /**
     * Tests that {@link Map#keySet()} functions correctly.
     */
    @Test(groups="development")
    public void keySet() {
        debug(" * keySet: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
        Set<Integer> keys = cache.keySet();
        Integer key = keys.iterator().next();
        int initialSize = cache.size();

        // key set reflects map
        assertEquals(keys, cache.data.keySet());
        try {
            assertFalse(keys.add(key));
            fail("Not supported by maps");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        assertTrue(keys.contains(key));
        assertTrue(keys.remove(key));
        assertFalse(keys.remove(key));
        assertFalse(keys.contains(key));
        assertEquals(keys.size(), initialSize-1);
        assertEquals(cache.size(), initialSize-1);

        // key iterator
        Iterator<Integer> iterator = keys.iterator();
        for (Node<Integer, Integer> node : cache.data.values()) {
            assertEquals(iterator.next(), node.getKey());
        }
        assertFalse(iterator.hasNext());

        Iterator<Integer> iter = keys.iterator();
        Integer i = iter.next();
        iter.remove();
        assertFalse(keys.contains(i));
        assertFalse(cache.containsKey(i));
        assertEquals(keys.size(), initialSize-2);
        assertEquals(cache.size(), initialSize-2);

        // toArray
        assertTrue(Arrays.equals(keys.toArray(), cache.data.keySet().toArray()));
        assertTrue(Arrays.equals(keys.toArray(new Integer[cache.size()]),
                                 cache.data.keySet().toArray(new Integer[cache.size()])));

        // other
        cache.setCapacity(capacity/2);
        assertEquals(keys.size(), capacity/2);

        keys.clear();
        assertTrue(cache.isEmpty());
        assertTrue(keys.isEmpty());
    }

    /**
     * Tests that {@link Map#values()} functions correctly.
     */
    @Test(groups="development")
    public void values() {
        debug(" * values: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
        Collection<Integer> values = cache.values();
        Integer value = values.iterator().next();
        int initialSize = cache.size();

        // value collection reflects map
        try {
            assertFalse(values.add(value));
            fail("Not supported by maps");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        assertTrue(values.contains(value));
        assertTrue(values.remove(value));
        assertFalse(values.remove(value));
        assertFalse(values.contains(value));
        assertEquals(values.size(), initialSize-1);
        assertEquals(cache.size(), initialSize-1);

        // values iterator
        Iterator<Integer> iterator = values.iterator();
        for (Node<Integer, Integer> node : cache.data.values()) {
            assertEquals(iterator.next(), node.getValue());
        }
        assertFalse(iterator.hasNext());

        Iterator<Integer> iter = values.iterator();
        Integer i = iter.next();
        iter.remove();
        assertFalse(values.contains(i));
        assertFalse(cache.containsValue(i));
        assertEquals(values.size(), initialSize-2);
        assertEquals(cache.size(), initialSize-2);

        // toArray
        List<Integer> list = new ArrayList<Integer>();
        for (Node<Integer, Integer> node : cache.data.values()) {
            list.add(node.getValue());
        }
        assertTrue(Arrays.equals(values.toArray(), list.toArray()));
        assertTrue(Arrays.equals(values.toArray(new Integer[cache.size()]), list.toArray(new Integer[cache.size()])));

        // other
        cache.setCapacity(capacity/2);
        assertEquals(values.size(), capacity/2);

        values.clear();
        assertTrue(cache.isEmpty());
        assertTrue(values.isEmpty());
    }

    /**
     * Tests that {@link Map#entrySet()} functions correctly.
     */
    @Test(groups="development")
    public void entrySet() {
        debug(" * entrySet: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
        Set<Entry<Integer, Integer>> entries = cache.entrySet();
        Entry<Integer, Integer> entry = entries.iterator().next();
        int initialSize = cache.size();

        // Entry updates reflect map
        Integer oldValue = entry.getValue();
        assertEquals(entry.setValue(Integer.MIN_VALUE), oldValue);
        assertTrue(entry.getValue().equals(Integer.MIN_VALUE));
        assertTrue(cache.containsValue(Integer.MIN_VALUE));

        // entryset reflects map
        assertEquals(entries.size(), initialSize);
        assertTrue(entries.contains(entry));
        assertFalse(entries.add(entry));
        assertTrue(entries.remove(entry));
        assertFalse(entries.remove(entry));
        assertFalse(cache.containsKey(entry.getKey()));
        assertFalse(cache.containsValue(entry.getValue()));
        assertEquals(entries.size(), initialSize-1);
        assertEquals(cache.size(), initialSize-1);
        assertTrue(entries.add(entry));
        assertEquals(entries.size(), initialSize);

        // entry iterator
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (Entry<Integer, Integer> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        assertEquals(cache, map);

        Iterator<Entry<Integer, Integer>> iter = entries.iterator();
        Entry<Integer, Integer> e = iter.next();
        iter.remove();
        assertFalse(entries.contains(e));
        assertFalse(cache.containsKey(e.getKey()));
        assertEquals(entries.size(), initialSize-1);
        assertEquals(cache.size(), initialSize-1);

        // toArray
        List<Entry<Integer, Integer>> list = asList(entries.toArray((Entry<Integer, Integer>[]) new Entry[initialSize-1]));
        assertTrue(new HashSet<Entry<Integer, Integer>>(list).equals(entries));
        assertTrue(new HashSet(asList(entries.toArray())).equals(entries));

        // other
        cache.setCapacity(capacity/2);
        assertEquals(entries.size(), capacity/2);

        entries.clear();
        assertTrue(cache.isEmpty());
        assertTrue(entries.isEmpty());
    }

    /**
     * Tests {@link Object#equals(Object)}, {@link Object#hashCode()}, {@link Object#toString()}.
     */
    @Test(groups="development")
    public void object() {
        debug(" * object: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        Map<Integer, Integer> expected = new ConcurrentHashMap<Integer, Integer>(capacity);
        for (Integer i=0; i<capacity; i++) {
            expected.put(i, i);
        }
        assertEquals(cache, expected);
        assertEquals(cache.hashCode(), expected.hashCode());
        assertEquals(cache.toString(), expected.toString());
    }

    /**
     * Tests serialization.
     */
    @Test(groups="development")
    public void serialize() {
        debug(" * serialize: START");
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap(guard);
        Object cache = SerializationUtils.clone(expected);
        assertEquals(cache, expected);
        validator.state((ConcurrentLinkedHashMap<Integer, Integer>) cache);
    }

    /**
     * Tests that entries are evicted in FIFO order.
     */
    @Test(groups="development")
    public void evictAsFifo() {
        debug(" * evictAsFifo: START");
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(FIFO, capacity, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Tests that entries are evicted in FIFO order under a SECOND_CHANCE policy where none are saved.
     */
    @Test(groups="development")
    public void evictSecondChanceAsFifo() {
        debug(" * evictSecondChanceAsFifo: START");
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(SECOND_CHANCE, capacity, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order.
     */
    @Test(groups="development")
    public void evictAsSecondChance() {
        debug(" * evictAsSecondChance: START");
        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(capacity);
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(SECOND_CHANCE, capacity, monitor);
        for (Integer i=0; i<capacity; i++) {
            cache.put(i, i);
            if (i%2 == 0) {
                cache.get(i);
                expected.put(i, i);
                assertTrue(cache.data.get(i).isMarked());
            }
        }

        for (Integer i=capacity; i<(capacity+capacity/2); i++) {
            cache.put(i, i);
            expected.put(i, i);
        }

        validator.state(cache);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), capacity/2);
    }

    /**
     * Tests that a full scan was required to evict an entry.
     */
    @Test(groups="development")
    public void evictSecondChanceFullScan() {
        debug(" * evictSecondChanceFullScan: START");
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(SECOND_CHANCE, capacity, monitor);
        for (int i=0; i<capacity; i++) {
            cache.get(i);
        }
        validator.allNodesMarked(cache, true);
        assertEquals(cache.size(), capacity);

        cache.put(capacity, capacity);
        assertEquals(cache.size(), capacity);
        validator.allNodesMarked(cache, false);
        assertEquals(monitor.evicted.size(), 1);
    }

    /**
     * Tests that entries are evicted in LRU order.
     */
    @Test(groups="development")
    public void evictAsLru() {
        debug(" * evictAsLru: START");
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(LRU, 10);

        debug("Initial: %s", validator.printFwd(cache));
        assertTrue(cache.keySet().containsAll(asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // re-order
        cache.get(0);
        cache.get(1);
        cache.get(2);

        debug("Reordered #1: %s", validator.printFwd(cache));
        assertTrue(cache.keySet().containsAll(asList(3, 4, 5, 6, 7, 8, 9, 0, 1, 2)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // evict 3, 4, 5
        cache.put(10, 10);
        cache.put(11, 11);
        cache.put(12, 12);

        debug("Evict #1: %s", validator.printFwd(cache));
        assertTrue(cache.keySet().containsAll(asList(6, 7, 8, 9, 0, 1, 2, 10, 11, 12)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // re-order
        cache.get(6);
        cache.get(7);
        cache.get(8);

        debug("Reordered #2: %s", validator.printFwd(cache));
        assertTrue(cache.keySet().containsAll(asList(9, 0, 1, 2, 10, 11, 12, 6, 7, 8)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // evict 9, 0, 1
        cache.put(13, 13);
        cache.put(14, 14);
        cache.put(15, 15);

        debug("Evict #2: %s", validator.printFwd(cache));
        assertTrue(cache.keySet().containsAll(asList(2, 10, 11, 12, 6, 7, 8, 13, 14, 15)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);
    }

    /**
     * Executes a FIFO eviction test.
     */
    private void doFifoEvictionTest(ConcurrentLinkedHashMap<Integer, Integer> cache, EvictionMonitor<Integer, Integer> monitor) {
        for (Integer i=0; i<3*capacity; i++) {
            cache.put(i, i);
        }

        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(capacity);
        for (Integer i=2*capacity; i<3*capacity; i++) {
            expected.put(i, i);
        }

        validator.state(cache);
        validator.allNodesMarked(cache, false);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), 2*capacity);
    }
}
