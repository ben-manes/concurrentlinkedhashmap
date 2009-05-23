/*
 * Copyright 2009 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reardencommerce.kernel.collections.shared.evictable;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link ConcurrentMap} with a doubly-linked list running through its entries.
 * <p>
 * This class provides the same semantics as a {@link ConcurrentHashMap} in terms of
 * iterators, acceptable keys, and concurrency characteristics, but perform slightly
 * worse due to the added expense of maintaining the linked list. It differs from
 * {@link java.util.LinkedHashMap} in that it does not provide predictable iteration
 * order.
 * <p>
 * This map is intended to be used for caches and provides the following eviction policies:
 * <ul>
 *   <li> First-in, First-out: Also known as insertion order. This policy has excellent
 *        concurrency characteristics and an adequate hit rate.
 *   <li> Second-chance: An enhanced FIFO policy that marks entries that have been retrieved
 *        and saves them from being evicted until the next pass. This enhances the FIFO policy
 *        by making it aware of "hot" entries, which increases its hit rate to be equal to an
 *        LRU's under normal workloads. In the worst case, where all entries have been saved,
 *        this policy degrades to a FIFO.
 *   <li> Least Recently Used: An eviction policy based on the observation that entries that
 *        have been used recently will likely be used again soon. This policy provides a good
 *        approximation of an optimal algorithm, but suffers by being expensive to maintain.
 *        The cost of reordering entries on the list during every access operation reduces
 *        the concurrency and performance characteristics of this policy.
 * </ul>
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 * @see    http://code.google.com/p/concurrentlinkedhashmap/
 */
public class ConcurrentLinkedHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
    private static final EvictionListener<?, ?> nullListener = new EvictionListener<Object, Object>() {
        public void onEviction(Object key, Object value) {}
    };
    private static final long serialVersionUID = 8350170357874293408L;
    final ConcurrentMap<K, Node<K, V>> data;
    final EvictionListener<K, V> listener;
    final AtomicInteger capacity;
    final EvictionPolicy policy;
    final AtomicInteger length;
    final Node<K, V> sentinel;

    /**
     * Creates a new, empty, unbounded map with the specified maximum capacity and the default
     * concurrencyLevel.
     *
     * @param policy          The eviction policy to apply when the size exceeds the maximum capacity.
     * @param maximumCapacity The maximum capacity to coerces to. The size may exceed it temporarily.
     */
    @SuppressWarnings("unchecked")
    public ConcurrentLinkedHashMap(EvictionPolicy policy, int maximumCapacity) {
        this(policy, maximumCapacity, 16, (EvictionListener<K, V>) nullListener);
    }

    /**
     * Creates a new, empty, unbounded map with the specified maximum capacity and the default
     * concurrencyLevel.
     *
     * @param policy          The eviction policy to apply when the size exceeds the maximum capacity.
     * @param maximumCapacity The maximum capacity to coerces to. The size may exceed it temporarily.
     * @param listener        The listener registered for notification when an entry is evicted.
     */
    public ConcurrentLinkedHashMap(EvictionPolicy policy, int maximumCapacity, EvictionListener<K, V> listener) {
        this(policy, maximumCapacity, 16, listener);
    }

    /**
     * Creates a new, empty, unbounded map with the specified maximum capacity and concurrency level.
     *
     * @param policy           The eviction policy to apply when the size exceeds the maximum capacity.
     * @param maximumCapacity  The maximum capacity to coerces to. The size may exceed it temporarily.
     * @param concurrencyLevel The estimated number of concurrently updating threads. The implementation
     *                         performs internal sizing to try to accommodate this many threads.
     */
    @SuppressWarnings("unchecked")
    public ConcurrentLinkedHashMap(EvictionPolicy policy, int maximumCapacity, int concurrencyLevel) {
        this(policy, maximumCapacity,  concurrencyLevel, (EvictionListener<K, V>) nullListener);
    }

    /**
     * Creates a new, empty, unbounded map with the specified maximum capacity and concurrency level.
     *
     * @param policy           The eviction policy to apply when the size exceeds the maximum capacity.
     * @param maximumCapacity  The maximum capacity to coerces to. The size may exceed it temporarily.
     * @param concurrencyLevel The estimated number of concurrently updating threads. The implementation
     *                         performs internal sizing to try to accommodate this many threads.
     * @param listener         The listener registered for notification when an entry is evicted.
     */
    public ConcurrentLinkedHashMap(EvictionPolicy policy, int maximumCapacity, int concurrencyLevel, EvictionListener<K, V> listener) {
        if ((policy == null) || (maximumCapacity < 0) || (concurrencyLevel <= 0) || (listener == null)) {
            throw new IllegalArgumentException();
        }
        this.data = new ConcurrentHashMap<K, Node<K, V>>(maximumCapacity, 0.75f, concurrencyLevel);
        this.capacity = new AtomicInteger(maximumCapacity);
        this.length = new AtomicInteger();
        this.sentinel = new Node<K, V>();
        this.listener = listener;
        this.policy = policy;
    }

    /**
     * Determines whether the map has exceeded its capacity.
     *
     * @return Whether the map has overflowed and an entry should be evicted.
     */
    private boolean isOverflow() {
        return size() > capacity();
    }

    /**
     * Sets the maximum capacity of the map and eagerly evicts entries until the
     * it shrinks to the appropriate size.
     *
     * @param capacity The maximum capacity of the map.
     */
    public void setCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException();
        }
        this.capacity.set(capacity);
        while (evict()) { }
    }

    /**
     * Retrieves the maximum capacity of the map.
     *
     * @return The maximum capacity.
     */
    public int capacity() {
        return capacity.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        int size = length.get();
        return (size >= 0) ? size : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        for (K key : keySet()) {
            remove(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return data.containsValue(new Node<Object, Object>(null, value, null));
    }

    /**
     * Evicts a single entry if the map exceeds the maximum capacity.
     */
    private boolean evict() {
        while (isOverflow()) {
            Node<K, V> node = sentinel.getNext();
            if (node == sentinel) {
                return false;
            } else if (policy.onEvict(this, node)) {
                // Attempt to remove the node if its still available
                if (data.remove(node.getKey(), new Identity(node))) {
                    length.decrementAndGet();
                    node.remove();
                    listener.onEviction(node.getKey(), node.getValue());
                    return true;
                }
            } else {
                if (node.tryRemove()) {
                    node.appendToTail();
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(Object key) {
        Node<K, V> node = data.get(key);
        if (node != null) {
            V value = node.getValue();
            policy.onAccess(this, node);
            return value;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V put(K key, V value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        Node<K, V> old = putIfAbsent(new Node<K, V>(key, value, sentinel));
        return (old == null) ? null : old.getAndSetValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        Node<K, V> old = putIfAbsent(new Node<K, V>(key, value, sentinel));
        return (old == null) ? null : old.getValue();
    }

    /**
     * Adds a node to the list and data store if it does not already exist.
     *
     * @param node An unlinked node to add.
     * @return     The previous value in the data store.
     */
    private Node<K, V> putIfAbsent(Node<K, V> node) {
        Node<K, V> old = data.putIfAbsent(node.getKey(), node);
        if (old == null) {
            length.incrementAndGet();
            node.appendToTail();
            evict();
        } else {
            policy.onAccess(this, old);
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(Object key) {
        Node<K, V> node = data.remove(key);
        if (node == null) {
            return null;
        }
        length.decrementAndGet();
        node.remove();
        return node.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object key, Object value) {
        Node<K, V> node = data.get(key);
        if ((node != null) && node.value.equals(value) && data.remove(key, new Identity(node))) {
            length.decrementAndGet();
            node.remove();
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        Node<K, V> node = data.get(key);
        return (node == null) ? null : node.getAndSetValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException();
        }
        Node<K, V> node = data.get(key);
        return (node == null) ? false : node.casValue(oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<V> values() {
        return new Values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * A listener registered for notification when an entry is evicted.
     */
    public interface EvictionListener<K, V> {

        /**
         * A call-back notification that the entry was evicted.
         *
         * @param key   The evicted key.
         * @param value The evicted value.
         */
        void onEviction(K key, V value);
    }

    /**
     * The replacement policy to apply to determine which entry to discard to when the capacity has been reached.
     */
    public enum EvictionPolicy {

        /**
         * Evicts entries based on insertion order.
         */
        FIFO() {
            @Override
            <K, V> void onAccess(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                // do nothing
            }
            @Override
            <K, V> boolean onEvict(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                return true;
            }
        },

        /**
         * Evicts entries based on insertion order, but gives an entry a "second chance" if it has been requested recently.
         */
        SECOND_CHANCE() {
            @Override
            <K, V> void onAccess(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                node.setMarked(true);
            }
            @Override
            <K, V> boolean onEvict(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                if (node.isMarked()) {
                    node.setMarked(false);
                    return false;
                }
                return true;
            }
        },

        /**
         * Evicts entries based on how recently they are used, with the least recent evicted first.
         */
        LRU() {
            @Override
            <K, V> void onAccess(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                if (node.tryRemove()) {
                    node.appendToTail();
                }
            }
            @Override
            <K, V> boolean onEvict(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node) {
                return true;
            }
        };

        /**
         * Performs any operations required by the policy after a node was successfully retrieved.
         */
        abstract <K, V> void onAccess(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node);

        /**
         * Determines whether to evict the node at the head of the list. If false, the node is offered to the tail.
         */
        abstract <K, V> boolean onEvict(ConcurrentLinkedHashMap<K, V> map, Node<K, V> node);
    }

    /**
     * A node on the double-linked list. This list cross-cuts the data store.
     */
    @SuppressWarnings("unchecked")
    static final class Node<K, V> implements Serializable {
        private static final long serialVersionUID = 1461281468985304519L;
        private static final AtomicReferenceFieldUpdater<Node, Object> valueUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Node.class, Object.class, "value");
        private static final AtomicReferenceFieldUpdater<Node, Node> prevUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "prev");
        private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");

        private final K key;
        private final Node<K, V> sentinel;

        private volatile V value;
        private volatile boolean marked;
        private volatile Node<K, V> prev;
        private volatile Node<K, V> next;

        /**
         * Creates a new sentinel node.
         */
        public Node() {
            this.sentinel = this;
            this.value = null;
            this.key = null;
            setPrev(this);
            setNext(this);
        }

        /**
         * Creates a new, unlinked node.
         */
        public Node(K key, V value, Node<K, V> sentinel) {
            this.sentinel = sentinel;
            this.value = value;
            this.key = key;
            setPrev(null);
            setNext(null);
        }

        /**
         * Removes the node from the list.
         */
        public void remove() {
            while (!tryRemove()) { /* retry */ }
        }

        /**
         * Attempts to remove the node from the list.
         *
         * @return Whether the node was removed.
         */
        public boolean tryRemove() {
            // Try to lock the node by shifting the "next" field to "null"
            // If successful, the previous value was captured in "auxiliary"
            final Node<K, V> auxiliary = getNext();
            if ((auxiliary != null) && casNext(auxiliary, null)) {
                // swing the previous node's "next" to our "next"
                while (!getPrev().casNext(this, auxiliary)) { /* spin */ }

                // swing the next node's "prev" to our "prev"
                while (!auxiliary.casPrev(this, getPrev())) { /* spin */ }

                // successfully remove the node
                return true;
            }
            // failed to remove the node
            return false;
        }

        /**
         * Appends the node to the tail of the list.
         */
        public void appendToTail() {
            // Initialize in the "locked" state
            setNext(null);

            // Link the tail to the new node
            do {
              setPrev(sentinel.getPrev());
            } while (!getPrev().casNext(sentinel, this));

            // Link the sentinel to the new tail
            while (!sentinel.casPrev(getPrev(), this)) {
                if (sentinel.getPrev() == this) {
                    break; // helped out
                }
            }

            // Unlock the new tail
            setNext(sentinel);
        }

        /*
         * Key operators
         */
        public K getKey() {
            return key;
        }

        /*
         * Value operators
         */
        public V getValue() {
            return (V) valueUpdater.get(this);
        }
        public V getAndSetValue(V value) {
            return (V) valueUpdater.getAndSet(this, value);
        }
        public boolean casValue(V expect, V update) {
            return valueUpdater.compareAndSet(this, expect, update);
        }

        /*
         * Previous node operators
         */
        public Node<K, V> getPrev() {
            return prevUpdater.get(this);
        }
        public void setPrev(Node<K, V> newValue) {
            prevUpdater.set(this, newValue);
        }
        public boolean casPrev(Node<K, V> expect, Node<K, V> update) {
            return prevUpdater.compareAndSet(this, expect, update);
        }

        /*
         * Next node operators
         */
        public Node<K, V> getNext() {
            return nextUpdater.get(this);
        }
        public void setNext(Node<K, V> newValue) {
            nextUpdater.set(this, newValue);
        }
        public boolean casNext(Node<K, V> expect, Node<K, V> update) {
            return nextUpdater.compareAndSet(this, expect, update);
        }

        /*
         * Access frequency operators
         */
        public boolean isMarked() {
            return marked;
        }
        public void setMarked(boolean marked) {
            this.marked = marked;
        }

        /**
         * Only ensures that the values are equal, as the key may be <tt>null</tt> for look-ups.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Node)) {
                return false;
            }
            V value = getValue();
            Node<?, ?> node = (Node<?, ?>) obj;
            return (value == null) ? (node.getValue() == null) : value.equals(node.getValue());
        }
        @Override
        public int hashCode() {
            return ((key   == null) ? 0 : key.hashCode()) ^
                   ((value == null) ? 0 : value.hashCode());
        }
        @Override
        public String toString() {
            return String.format("Node[key=%s, value=%s, marked=%b]", getKey(), getValue(), isMarked());
        }
    }

    /**
     * Allows {@link #equals(Object)} to compare using object identity.
     */
    private static final class Identity {
        private final Object delegate;

        public Identity(Object delegate) {
            this.delegate = delegate;
        }
        @Override
        public boolean equals(Object o) {
            return (o == delegate);
        }
    }

    /**
     * An adapter to safely externalize the keys.
     */
    private final class KeySet extends AbstractSet<K> {
        private final Set<K> keys = ConcurrentLinkedHashMap.this.data.keySet();

        @Override
        public int size() {
            return keys.size();
        }
        @Override
        public void clear() {
            ConcurrentLinkedHashMap.this.clear();
        }
        @Override
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        @Override
        public boolean contains(Object obj) {
            return keys.contains(obj);
        }
        @Override
        public boolean remove(Object obj) {
            return (ConcurrentLinkedHashMap.this.remove(obj) != null);
        }
        @Override
        public Object[] toArray() {
            return keys.toArray();
        }
        @Override
        public <T> T[] toArray(T[] array) {
            return keys.toArray(array);
        }
    }

    /**
     * An adapter to safely externalize the keys.
     */
    private final class KeyIterator implements Iterator<K> {
        private final EntryIterator iterator = new EntryIterator(ConcurrentLinkedHashMap.this.data.values().iterator());

        public boolean hasNext() {
            return iterator.hasNext();
        }
        public K next() {
            return iterator.next().getKey();
        }
        public void remove() {
            iterator.remove();
        }
    }

    /**
     * An adapter to represent the data store's values in the external type.
     */
    private final class Values extends AbstractCollection<V> {
        private final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

        @Override
        public int size() {
            return map.size();
        }
        @Override
        public void clear() {
            map.clear();
        }
        @Override
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        @Override
        public boolean contains(Object o) {
            return map.containsValue(o);
        }
        @Override
        public Object[] toArray() {
            Collection<V> values = new ArrayList<V>(size());
            for (V value : this) {
                values.add(value);
            }
            return values.toArray();
        }
        @Override
        public <T> T[] toArray(T[] array) {
            Collection<V> values = new ArrayList<V>(size());
            for (V value : this) {
                values.add(value);
            }
            return values.toArray(array);
        }
    }

    /**
     * An adapter to represent the data store's values in the external type.
     */
    private final class ValueIterator implements Iterator<V> {
        private final EntryIterator iterator = new EntryIterator(ConcurrentLinkedHashMap.this.data.values().iterator());

        public boolean hasNext() {
            return iterator.hasNext();
        }
        public V next() {
            return iterator.next().getValue();
        }
        public void remove() {
            iterator.remove();
        }
    }

    /**
     * An adapter to represent the data store's entry set in the external type.
     */
    private final class EntrySet extends AbstractSet<Entry<K, V>> {
        private final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

        @Override
        public int size() {
            return map.size();
        }
        @Override
        public void clear() {
            map.clear();
        }
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator(map.data.values().iterator());
        }
        @Override
        public boolean contains(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            Node<K, V> node = map.data.get(entry.getKey());
            return (node != null) && (node.value.equals(entry.getValue()));
        }
        @Override
        public boolean add(Entry<K, V> entry) {
            return (map.putIfAbsent(entry.getKey(), entry.getValue()) == null);
        }
        @Override
        public boolean remove(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            return map.remove(entry.getKey(), entry.getValue());
        }
        @Override
        public Object[] toArray() {
            Collection<Entry<K, V>> entries = new ArrayList<Entry<K, V>>(size());
            for (Entry<K, V> entry : this) {
                entries.add(new SimpleEntry<K, V>(entry));
            }
            return entries.toArray();
        }
        @Override
        public <T> T[] toArray(T[] array) {
            Collection<Entry<K, V>> entries = new ArrayList<Entry<K, V>>(size());
            for (Entry<K, V> entry : this) {
                entries.add(new SimpleEntry<K, V>(entry));
            }
            return entries.toArray(array);
        }
    }

    /**
     * An adapter to represent the data store's entry iterator in the external type.
     */
    private final class EntryIterator implements Iterator<Entry<K, V>> {
        private final Iterator<Node<K, V>> iterator;
        private Entry<K, V> current;

        public EntryIterator(Iterator<Node<K, V>> iterator) {
            this.iterator = iterator;
        }
        public boolean hasNext() {
            return iterator.hasNext();
        }
        public Entry<K, V> next() {
            Node<K, V> node = iterator.next();
            current = new SimpleEntry<K, V>(node.getKey(), node.getValue());
            return current;
        }
        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
            ConcurrentLinkedHashMap.this.remove(current.getKey(), current.getValue());
            current = null;
        }
    }

    /**
     * This duplicates {@link java.util.AbstractMap.SimpleEntry} until the class is made accessible (public in JDK-6).
     */
    private static final class SimpleEntry<K,V> implements Entry<K,V> {
        private final K key;
        private V value;

        public SimpleEntry(K key, V value) {
            this.key   = key;
            this.value = value;
        }
        public SimpleEntry(Entry<K, V> e) {
            this.key   = e.getKey();
            this.value = e.getValue();
        }
        public K getKey() {
            return key;
        }
        public V getValue() {
            return value;
        }
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            return eq(key, entry.getKey()) && eq(value, entry.getValue());
        }
        @Override
        public int hashCode() {
            return ((key   == null) ? 0 :   key.hashCode()) ^
                   ((value == null) ? 0 : value.hashCode());
        }
        @Override
        public String toString() {
            return key + "=" + value;
        }
        private static boolean eq(Object o1, Object o2) {
            return (o1 == null) ? (o2 == null) : o1.equals(o2);
        }
    }
}
