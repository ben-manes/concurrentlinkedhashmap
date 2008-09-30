package com.rc.util.concurrent;

import static java.lang.String.format;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.Test;

/**
 * The non-concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public final class SingleThreadedTest extends BaseTest {

    /**
     * Tests {@link ConcurrentLinkedHashMap#ConcurrentLinkedHashMap(int)} is empty.
     */
    @Test(groups="development")
    public void empty() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        validator.state(cache);
        validator.empty(cache);
    }

    /**
     * Tests {@link Map#putAll(Map)}.
     */
    @Test(groups="development")
    public void putAll() {
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
        ConcurrentLinkedHashMap<Integer, Integer> cache = create();
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
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        for (Integer i=0; i<capacity; i++) {
            assertNull(cache.putIfAbsent(i, i));
            assertEquals(cache.putIfAbsent(i, -1), i);
            assertEquals(cache.data.get(i).getValue(), i);
        }
        assertEquals(cache.size(), capacity, "Not warmed to max size");
        validator.state(cache);
        validator.allNodesMarked(cache, false);
        assertEquals(cache, createWarmedMap());
    }

    /**
     * Tests {@link Map#containsKey(Object)}, {@link Map#containsValue(Object)}, {@link Map#get(Object)}.
     */
    @Test(groups="development")
    public void retrieval() {
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
     * Tests {@link Map#remove()} and {@link java.util.concurrent.ConcurrentMap#remove(Object, Object)}
     */
    @Test(groups="development")
    public void remove() {
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
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

        int newMaxCapacity = 2*capacity;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache, createWarmedMap());
        validator.state(cache);

        newMaxCapacity = capacity/2;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);

        newMaxCapacity = 1;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);

        try {
            cache.setCapacity(-1);
            fail("Capacity must be positive");
        } catch (Exception e) {
            assertEquals(cache.capacity(), newMaxCapacity);
        }
    }

    /**
     * Tests {@link Object#equals(Object)}, {@link Object#hashCode()}, {@link Object#toString()}.
     */
    @Test(groups="development")
    public void object() {
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
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap(guard);
        Object cache = SerializationUtils.clone(expected);
        assertEquals(cache, expected);
        validator.state((ConcurrentLinkedHashMap<Integer, Integer>) cache);
    }
}
