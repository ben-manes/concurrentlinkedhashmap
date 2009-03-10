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
package com.rc.util.concurrent;

import static com.rc.util.concurrent.ConcurrentLinkedHashMap2.Element.newElement;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link ConcurrentMap} with a linked list running through its entries.
 * <p>
 * This class provides the same semantics as a {@link ConcurrentHashMap} in terms of iterators, acceptable keys, and
 * concurrency characteristics, but perform slightly worse due to the added expense of maintaining the linked list.
 * It differs from {@link java.util.LinkedHashMap} in that it does not provide predictable iteration order.
 * <p>
 * This map is intended to be used for caches and provides the following eviction policies:
 * <ul>
 *   <li> <b>First-in, First-out:</b> Also known as insertion order. This policy has excellent concurrency
 *        characteristics and an adequate hit rate.
 *   <li> <b>Second-chance:</b> An enhanced FIFO policy that marks entries that have been retrieved and saves them from
 *        being evicted until the next pass. This enhances the FIFO policy by making it aware of "hot" entries, which
 *        increases its hit rate to be equal to an LRU's under normal workloads. In the worst case, where all entries
 *        have been saved, this policy degrades to a FIFO.
 *   <li> <b>Least Recently Used:</b> An eviction policy based on the observation that entries that have been used
 *        recently will likely be used again soon. This policy provides a good approximation of an optimal algorithm,
 *        but suffers by being expensive to maintain. The cost of reordering entries on the list during every access
 *        operation reduces the concurrency and performance characteristics of this policy.
 * </ul>
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 * @see    http://code.google.com/p/concurrentlinkedhashmap/
 */
/*
 * PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE
 *
 * Do not use. This is an early implementation. It is not correct, not beautiful, and not even tested!
 * When complete, this class will implement: http://code.google.com/p/concurrentlinkedhashmap/wiki/Design
 *
 * PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE  -  PROTOTYPE
 */
class ConcurrentLinkedHashMap2<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 8350170357874293408L;
    private final ConcurrentMap<K, Element<K, V>> data;
    private final EvictionListener<K, V> listener;
    private final Element<K, V> sentinel;
    private final AtomicInteger capacity;
    private final EvictionPolicy policy;
    private final AtomicInteger length;

    private ConcurrentLinkedHashMap2(Builder<K, V> builder) {
        data = new ConcurrentHashMap<K, Element<K, V>>(builder.capacity, 0.75f, builder.concurrencyLevel);
        capacity = new AtomicInteger(builder.capacity);
        length = new AtomicInteger();
        listener = builder.listener;
        policy = builder.policy;
        sentinel = null;
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
     * Sets the maximum capacity of the map and eagerly evicts entries until the it shrinks to the appropriate size.
     *
     * @param capacity The maximum capacity of the map.
     */
    public void setCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException();
        }
        this.capacity.set(capacity);
        while (isOverflow()) {
            evict();
        }
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
        return length.get();
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
        return data.containsValue(newElement(null, value));
    }

    /**
     * Evicts a single entry if the map exceeds the maximum capacity.
     */
    private void evict() {
        while (isOverflow()) {
            Element<K, V> element = findVictim();
            if (element == null) {
                return;
            } else if (policy.onEvict(this, element)) {
                if (data.remove(element.getKey()) == null) {
                    // check for concurrent removal, and if so unlock
                    element.unlock();
                } else {
                    remove(element);
                    length.decrementAndGet();
                    listener.onEviction(element.getKey(), element.getValue());
                }
                return;
            } else {
                moveToTail(element);
            }
        }
    }

    /**
     * Retrieves and removes the first node on the list or <tt>null</tt> if empty.
     *
     * @return The first node on the list or <tt>null</tt> if empty.
     */
    private Element<K, V> findVictim() {
        // 1) walk the list, always checking if still overflow
        // 2) tryLock
        //   a) if unsuccessful continue to walk
        //   b) if successful, return - do not remove!
        // 3) if end of list, restart from head (1)
        // 4) if no longer overflow, return null

        for (;;) {
            Element<K, V> current = getNextElement(sentinel);
            if (current == sentinel) {
                return null;
            }
            do {
                if (current.tryLock()) {
                    return current;
                }
                if (!isOverflow()) {
                    return null;
                }
                current = getNextElement(current);
            } while (current != sentinel);
        }
    }

    private Element<K, V> getNextElement(Element<K, V> element) {
        return element.getNext().getNext();
    }

    /**
     * Inserts the element at the tail of the list. Assumes that both the element and its auxiliary locks were acquired.
     *
     * @param node An unlinked node to append to the tail of the list.
     */
    private void offer(Element<K, V> element) {
        Auxiliary<K, V> prev = sentinel.lockPrev();
        prev.setNext(element);
        element.setPrev(prev);
        sentinel.setPrev(element.getNext());
        element.getPrev().unlock();
        element.getNext().unlock();
        element.unlock();
    }

    private void moveToTail(Element<K, V> element) {
        remove(element);
        offer(element);
        element.unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(Object key) {
        Element<K, V> element = data.get(key);
        if (element == null) {
            return null;
        }
        policy.onGet(this, element);
        return element.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V put(K key, V value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException();
        }
        Element<K, V> old = putIfAbsent(newElement(key, value));
        return (old == null) ? null : old.getAndSetValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException();
        }
        Element<K, V> old = putIfAbsent(newElement(key, value));
        if (old == null) {
            return null;
        }
        policy.onGet(this, old);
        return old.getValue();
    }

    /**
     * Adds an element to the list and the data store if it does not already exist.
     *
     * @param element An unlinked element to add.
     * @return        The previous element in the data store.
     */
    private Element<K, V> putIfAbsent(Element<K, V> element) {
        Element<K, V> old = data.putIfAbsent(element.getKey(), element);
        if (old == null) {
            length.incrementAndGet();
            offer(element);
            evict();
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(Object key) {
        Element<K, V> element = data.remove(key);
        if (element == null) {
            return null;
        }
        remove(element);
        return element.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object key, Object value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException();
        }
        Element<K, V> element = data.get(key);
        // FIXME: race condition - use instance equals
        if ((element != null) && value.equals(element.getValue()) && data.remove(key, element)) {
            element.lock();
            remove(element);
            return true;
        }
        return false;
    }

    /**
     * Removes the element. Assumes that the lock has already been acquired.
     */
    private void remove(Element<K, V> element) {
        Auxiliary<K, V> prev = element.lockPrev();
        Auxiliary<K, V> next = element.lockNext();

        Element<K, V> nextElement = next.getNext();
        nextElement.setPrev(prev);
        prev.setNext(nextElement);

        prev.unlock();
    }

    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException();
        }
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        if ((key == null) || (oldValue == null) || (newValue == null)) {
            throw new IllegalArgumentException();
        }
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<K> keySet() {
        return data.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * Retrieves the keys in the list's order.
     *
     * @return
     */
    public Iterator<K> orderedKeys() {
        return new LinkKeyIterator();
    }

    /**
     * Retrieves the values in the list's order.
     *
     * @return
     */
    public Iterator<V> orderedValues() {
        return new LinkValueIterator();
    }

    /**
     * Retrieves the entries in the list's order.
     *
     * @return
     */
    public Iterator<Entry<K, V>> orderedEntries() {
        return new LinkEntryIterator();
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

    public static final class Builder<K, V> {
        private static final EvictionListener<?, ?> nullListener = new EvictionListener<Object, Object>() {
            public void onEviction(Object key, Object value) {}
        };
        private final EvictionPolicy policy;
        private final int capacity;

        private EvictionListener<K, V> listener;
        private int concurrencyLevel;

        @SuppressWarnings("unchecked")
        public Builder(EvictionPolicy polcy, int capacity) {
            this.listener = (EvictionListener<K, V>) nullListener;
            this.capacity = capacity;
            this.policy = polcy;
        }

        public Builder<K, V> setListener(EvictionListener<K, V> listener) {
            this.listener = listener;
            return this;
        }
        public Builder<K, V> setConcurrencyLevel(int concurrencyLevel) {
            this.concurrencyLevel = concurrencyLevel;
            return this;
        }
        public ConcurrentLinkedHashMap2<K, V> build() {
            return new ConcurrentLinkedHashMap2<K, V>(this);
        }
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
            <K, V> void onGet(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element) {
                // do nothing
            }
        },

        /**
         * Evicts entries based on insertion order, but gives an entry a "second chance" if it has been requested recently.
         */
        SECOND_CHANCE() {
            @Override
            <K, V> void onGet(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element) {
                element.setMarked(true);
            }
            @Override
            <K, V> boolean onEvict(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element) {
                if (element.isMarked()) {
                    element.setMarked(false);
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
            <K, V> void onGet(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element) {
                if (element.tryLock()) {
                    map.remove(element);
                    map.offer(element);
                }
            }
        };

        /**
         * Performs any operations required by the policy after a node was successfully retrieved.
         */
        abstract <K, V> void onGet(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element);

        /**
         * Determines whether to evict the node at the head of the list. If false, the node is offered to the tail.
         */
        <K, V> boolean onEvict(ConcurrentLinkedHashMap2<K, V> map, Element<K, V> element) {
            return true;
        }
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    private static final class Auxiliary<K, V> {
        private static final AtomicReferenceFieldUpdater<Auxiliary, Boolean> lockUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Auxiliary.class, Boolean.class, "lock");

        private volatile Element<K, V> next;
        private volatile boolean lock;

        public Element<K, V> getNext() {
            return next;
        }
        public void setNext(Element<K, V> next) {
            this.next = next;
        }

        public boolean tryLock() {
            return lockUpdater.compareAndSet(this, Boolean.FALSE, Boolean.TRUE);
        }
        public void unlock() {
            lock = Boolean.FALSE;
        }
    }

    @SuppressWarnings("unchecked")
    static final class Element<K, V> {
        private static final AtomicReferenceFieldUpdater<Element, Boolean> lockUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Element.class, Boolean.class, "lock");
        private static final AtomicReferenceFieldUpdater<Element, Object> valueUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Element.class, Object.class, "value");

        private volatile Auxiliary<K, V> prev;
        private volatile Auxiliary<K, V> next;
        private volatile boolean marked;
        private volatile Boolean lock;
        private volatile V value;
        private final K key;

        public Element(K key, V value) {
            this.key = key;
        }

        public static <K, V> Element<K, V> newElement(K key, V value) {
            return new Element<K, V>(key, value);
        }


        public K getKey() {
            return key;
        }

        /*
         * Value operators
         */
        public V getValue() {
            return value;
        }
        public void setValue(V value) {
            this.value = value;
        }
        public V getAndSetValue(V value) {
            return (V) valueUpdater.getAndSet(this, value);
        }
        public boolean casValue(V expect, V update) {
            return valueUpdater.compareAndSet(this, expect, update);
        }

        public Auxiliary<K, V> getNext() {
            return next;
        }

        public Auxiliary<K, V> getPrev() {
            return prev;
        }
        public void setPrev(Auxiliary<K, V> prev) {
            this.prev = prev;
        }

        public boolean tryLock() {
            return lockUpdater.compareAndSet(this, Boolean.FALSE, Boolean.TRUE);
        }
        public void lock() {
            while (!tryLock()) {}
        }
        public void unlock() {
            lock = Boolean.FALSE;
        }

        public Auxiliary<K, V> lockPrev() {
            for (;;) {
                Auxiliary aux = getPrev();
                if (aux.tryLock()) {
                    return aux;
                }
            }
        }
        public void unlockPrev() {
            getPrev().unlock();
        }

        public Auxiliary<K, V> lockNext() {
            for (;;) {
                Auxiliary aux = getNext();
                if (aux.tryLock()) {
                    return aux;
                }
            }
        }
        public void unlockNext() {
            getNext().unlock();
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
            } else if (!(obj instanceof Element)) {
                return false;
            }
            V value = getValue();
            Element<?, ?> element = (Element<?, ?>) obj;
            return (value == null) ? (element.getValue() == null) : value.equals(element.getValue());
        }
        @Override
        public int hashCode() {
            return ((getKey() == null) ? 0 : getKey().hashCode()) ^ ((getValue() == null) ? 0 : getValue().hashCode());
        }
        @Override
        public String toString() {
            return String.format("Element[key=%s, value=%s]", getKey(), getValue());
        }
    }

    /**
     * An adapter to represent the data store's entry set in the external type.
     */
    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        @Override
        public Iterator<Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        @Override
        public boolean contains(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            Element<K, V> element = data.get(entry.getKey());
            return (element != null) && (element.getValue().equals(entry.getValue()));
        }
        @Override
        public boolean remove(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            return ConcurrentLinkedHashMap2.this.remove(entry.getKey(), entry.getValue());
        }
        @Override
        public int size() {
            return ConcurrentLinkedHashMap2.this.size();
        }
        @Override
        public void clear() {
            ConcurrentLinkedHashMap2.this.clear();
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
        private final Iterator<Entry<K, Element<K, V>>> iterator;
        private Entry<K, Element<K, V>> current;

        public EntryIterator() {
            this.iterator = data.entrySet().iterator();
        }
        public boolean hasNext() {
            return iterator.hasNext();
        }
        public Entry<K, V> next() {
            current = iterator.next();
            return new SimpleEntry<K, V>(current.getKey(), current.getValue().getValue());
        }
        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
            ConcurrentLinkedHashMap2.this.remove(current.getKey());
            current = null;
        }
    }

    /**
     * An {@link Iterator} that walks the linked list and retrieves elements in order.
     */
    private final class LinkIterator implements Iterator<Element<K, V>> {
        private Element<K, V> next;
        private Element<K, V> current;

        public LinkIterator() {
            current = null;
            next = sentinel.getNext().getNext();
        }
        public boolean hasNext() {
            return (next == sentinel);
        }
        public Element<K, V> next() {
            current = next;
            next = next.getNext().getNext();
            return current;
        }
        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
            ConcurrentLinkedHashMap2.this.remove(current.getKey());
            current = null;
        }
    }

    /**
     * An {@link Iterator} that walks the linked list and retrieves keys in order.
     */
    private final class LinkKeyIterator implements Iterator<K> {
        private final LinkIterator elements = new LinkIterator();

        public boolean hasNext() {
            return elements.hasNext();
        }
        public K next() {
            return elements.next().getKey();
        }
        public void remove() {
            elements.remove();
        }
    }

    /**
     * An {@link Iterator} that walks the linked list and retrieves values in order.
     */
    private final class LinkValueIterator implements Iterator<V> {
        private final LinkIterator elements = new LinkIterator();

        public boolean hasNext() {
            return elements.hasNext();
        }
        public V next() {
            return elements.next().getValue();
        }
        public void remove() {
            elements.remove();
        }
    }

    /**
     * An {@link Iterator} that walks the linked list and retrieves entries in order.
     */
    private final class LinkEntryIterator implements Iterator<Entry<K, V>> {
        private final LinkIterator elements = new LinkIterator();

        public boolean hasNext() {
            return elements.hasNext();
        }
        public Entry<K, V> next() {
            Element<K, V> element = elements.next();
            return new SimpleEntry<K, V>(element.getKey(), element.getValue());
        }
        public void remove() {
            elements.remove();
        }
    }

    /**
     * This duplicates {@link java.util.AbstractMap.SimpleEntry} until the class is made accessible (done so in JDK6).
     */
    private static final class SimpleEntry<K,V> implements Entry<K,V> {
        private final K key;
        private V value;

        public SimpleEntry(Entry<K, V> entry) {
            this(entry.getKey(), entry.getValue());
        }
        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
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