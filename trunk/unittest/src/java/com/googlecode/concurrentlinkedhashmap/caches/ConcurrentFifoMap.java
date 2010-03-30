package com.googlecode.concurrentlinkedhashmap.caches;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ConcurrentMap} that evicts elements in FIFO order once the capacity is reached.
 *
 * <b>Note: This was the 1st prototype of a fast caching algorithm.</b>
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
final class ConcurrentFifoMap<K, V> implements ConcurrentMap<K, V> {
    private final ConcurrentMap<K, V> map;
    private final AtomicInteger capacity;
    private final AtomicInteger size;
    private final Queue<K> queue;

    public ConcurrentFifoMap(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("The capacity must be positive");
        }
        this.map = new ConcurrentHashMap<K, V>(capacity, 0.75f, 10000);
        this.queue = new ConcurrentLinkedQueue<K>();
        this.capacity = new AtomicInteger(capacity);
        this.size = new AtomicInteger();
    }

    /**
     * Retrieves the maximum capacity of the queue.
     *
     * @return The queue's capacity.
     */
    public int capacity() {
        return capacity.get();
    }

    /**
     * Sets the maximum capacity of the map and eagerly evicts entries until the
     * queue shrinks to the appropriate size.
     *
     * @param capacity The maximum capacity of the queue.
     */
    public void setMaximumCapacity(int capacity) {
        this.capacity.set(capacity);
        while (size() > capacity()) {
            evict();
        }
    }

    /**
     * Evicts a single entry from the map if it exceeds capacity.
     */
    private void evict() {
        while (size() > capacity()) {
            K key = queue.poll();
            if ((key == null) || (remove(key) != null)) {
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        queue.clear();
        map.clear();
        size.set(map.size());
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return map.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    public V get(Object key) {
        return map.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return map.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public Set<K> keySet() {
        return map.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(Map<? extends K, ? extends V> t) {
        for (Entry<? extends K, ? extends V> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    public V put(K key, V value) {
        V old = map.put(key, value);
        if (old == null) {
            size.incrementAndGet();
            queue.offer(key);
            evict();
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        V old = map.putIfAbsent(key, value);
        if (old == null) {
            size.incrementAndGet();
            queue.offer(key);
            evict();
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object key, Object value) {
        if (map.remove(key, value)) {
            size.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public V remove(Object key) {
        V value = map.remove(key);
        if (value != null) {
            size.decrementAndGet();
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        return map.replace(key, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        return map.replace(key, value);
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return size.get();
    }

    /**
     * {@inheritDoc}
     */
    public Collection<V> values() {
        return map.values();
    }
}
