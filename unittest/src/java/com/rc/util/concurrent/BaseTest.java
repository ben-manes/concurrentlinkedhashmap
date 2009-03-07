package com.rc.util.concurrent;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionListener;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * Base utilities for testing purposes.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public abstract class BaseTest extends Assert {
    protected final EvictionMonitor<Integer, Integer> guard = EvictionMonitor.newGuard();
    protected final EvictionPolicy defaultPolicy = EvictionPolicy.SECOND_CHANCE;
    protected Validator validator;
    protected boolean debug;
    protected int capacity;

    /**
     * Initializes the test with runtime properties.
     */
    @BeforeClass(alwaysRun=true)
    public void before() {
        validator = new Validator(Boolean.valueOf(System.getProperty("test.exhaustive")));
        capacity = Integer.valueOf(System.getProperty("test.maximumCapacity"));
        debug = Boolean.valueOf(System.getProperty("test.debugMode"));

        info("%s:\n", getClass().getSimpleName());
    }

    /**
     * Logs a statement.
     */
    protected void info(String message, Object... args) {
        System.out.printf(message, args);
        System.out.println();
    }

    /**
     * Logs a statement, if debugging is enabled.
     */
    protected void debug(String message, Object... args) {
        if (debug) {
            info(message, args);
        }
    }

    protected <K, V> ConcurrentLinkedHashMap<K, V> create() {
        return create(defaultPolicy);
    }
    protected <K, V> ConcurrentLinkedHashMap<K, V> createGuarded() {
        return create(defaultPolicy, EvictionMonitor.<K, V>newGuard());
    }
    protected <K, V> ConcurrentLinkedHashMap<K, V> create(EvictionPolicy policy) {
        return new ConcurrentLinkedHashMap<K, V>(policy, capacity);
    }
    protected <K, V> ConcurrentLinkedHashMap<K, V> create(EvictionPolicy policy, EvictionListener<K, V> listener) {
        return new ConcurrentLinkedHashMap<K, V>(policy, capacity, listener);
    }

    /**
     * Creates a map warmed to the specified maximum capacity.
     */
    protected ConcurrentLinkedHashMap<Integer, Integer> createWarmedMap() {
        return createWarmedMap(defaultPolicy, capacity);
    }
    protected ConcurrentLinkedHashMap<Integer, Integer> createWarmedMap(EvictionListener<Integer, Integer> listener) {
        return createWarmedMap(defaultPolicy, capacity, listener);
    }
    protected ConcurrentLinkedHashMap<Integer, Integer> createWarmedMap(EvictionPolicy policy, int size, EvictionListener<Integer, Integer> listener) {
        return warm(new ConcurrentLinkedHashMap<Integer, Integer>(policy, size, listener), size);
    }
    protected ConcurrentLinkedHashMap<Integer, Integer> createWarmedMap(EvictionPolicy policy, int size) {
        return warm(new ConcurrentLinkedHashMap<Integer, Integer>(policy, size), size);
    }

    protected ConcurrentLinkedHashMap<Integer, Integer> warm(ConcurrentLinkedHashMap<Integer, Integer> cache, int size) {
        for (Integer i=0; i<size; i++) {
            assertNull(cache.put(i, i));
            assertEquals(cache.data.get(i).getValue(), i);
        }
        validator.allNodesMarked(cache, false);
        assertEquals(cache.size(), size, "Not warmed to max size");
        return cache;
    }

    protected static final class EvictionMonitor<K, V> implements EvictionListener<K, V>, Serializable {
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
}
