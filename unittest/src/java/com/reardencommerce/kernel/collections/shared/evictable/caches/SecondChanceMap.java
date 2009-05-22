package com.reardencommerce.kernel.collections.shared.evictable.caches;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ConcurrentMap} that evicts elements once the capacity is reached.
 *
 * This implementation uses a second-chance FIFO algorithm. This trades off
 * slightly worse efficiency than an LRU for a simpler, faster approach with
 * much lower lock contention.
 *
 * <b>Note: This was the 2nd prototype of a fast caching algorithm.</b>
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
final class SecondChanceMap<K, V> implements ConcurrentMap<K, V> {
    private final ConcurrentMap<K, Value<V>> map;
    private final AtomicInteger capacity;
    private final AtomicInteger size;
    private final Queue<K> queue;

    /**
     * A self-evicting map that is bounded to a maximum capacity.
     *
     * @param capacity     The maximum size that the map can grow to.
     */
    public SecondChanceMap(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("The capacity must be positive");
        }
        this.map = new ConcurrentHashMap<K, Value<V>>(capacity, 0.75f, 10000);
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
            if (key == null) {
                return;
            }
            Value<V> value = map.remove(key);
            if (value != null) {
                if (value.isSavedAndReset() && (map.putIfAbsent(key, value) == null)) {
                    queue.offer(key);
                } else {
                    size.decrementAndGet();
                }
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
        return map.containsValue(new Value<Object>(value));
    }

    /**
     * {@inheritDoc}
     */
    public Set<Entry<K, V>> entrySet() {
        return null;
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
        Value<V> value = map.get(key);
        if (value != null) {
            value.save();
            return value.getValue();
        }
        return null;
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
        Value<V> old = map.put(key, new Value<V>(value));
        if (old == null) {
            size.incrementAndGet();
            queue.offer(key);
            evict();
            return null;
        }
        return old.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        Value<V> old = map.putIfAbsent(key, new Value<V>(value));
        if (old == null) {
            size.incrementAndGet();
            queue.offer(key);
            evict();
            return null;
        }
        return old.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean remove(Object key, Object value) {
        if (map.remove(key, new Value<V>((V) value))) {
            size.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public V remove(Object key) {
        Value<V> old = map.remove(key);
        if (old != null) {
            size.decrementAndGet();
            return old.getValue();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        return map.replace(key, new Value<V>(oldValue), new Value<V>(newValue));
    }

    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        return map.replace(key, new Value<V>(value)).getValue();
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
        return null;
    }

    private static final class Value<V> {
        private final V value;
        private final AtomicBoolean saved;

        public Value(V value) {
            if (value == null) {
                throw new IllegalArgumentException("Null value");
            }
            this.value = value;
            this.saved = new AtomicBoolean();
        }
        public V getValue() {
            return value;
        }
        public void save() {
            saved.set(true);
        }
        public boolean isSavedAndReset() {
            return saved.getAndSet(false);
        }
    }
}
