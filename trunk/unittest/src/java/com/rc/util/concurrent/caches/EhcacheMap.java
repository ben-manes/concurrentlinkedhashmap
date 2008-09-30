package com.rc.util.concurrent.caches;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

/**
 * Exposes <tt>ehcache</tt> as a {@link Map}.
 * 
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
final class EhcacheMap<K, V> extends AbstractMap<K, V> {
    private final Ehcache cache;

    /**
     * @param accessOrder The eviction policy: true=LRU, false=FIFO.
     * @param capacity    The maximum capacity of the map.
     */
    public EhcacheMap(boolean accessOrder, int capacity) {
        MemoryStoreEvictionPolicy policy = (accessOrder ? MemoryStoreEvictionPolicy.LRU : MemoryStoreEvictionPolicy.FIFO);
        cache = new Cache(Cache.DEFAULT_CACHE_NAME, capacity, policy, false, null, false, 0, 0, false, 0, null, null, 0, 0);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        cache.removeAll();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return keySet().size();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return cache.isKeyInCache(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return cache.isValueInCache(value);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public V get(Object key) {
        Element element = cache.get(key);
        return (element == null) ? null : (V) element.getObjectValue();
    }

    /**
     * {@inheritDoc}
     */
    public Map<K, V> getAll(Collection<? extends K> keys) {
        Map<K, V> results = new HashMap<K, V>(keys.size());
        for (K key : keys) {
            V value = get(key);
            if (value != null) {
                results.put(key, value);
            }
        }
        return results;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public V put(K key, V value) {
        V old = get(key);
        cache.put(new Element(key, value));
        return old;
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        V old = get(key);
        if (old == null) {
            cache.put(new Element(key, value));
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(Object key) {
        V old = get(key);
        if (old != null) {
            cache.remove(key);
        }
        return old;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean remove(Object key, Object value) {
        if (value.equals(get(key))) {
            cache.remove(key);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void removeAll(Collection<? extends K> keys) {
        for (K key : keys) {
            remove(key);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        V old = get(key);
        if (old != null) {
            cache.put(new Element(key, value));
        }
        return old;
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        if (oldValue.equals(get(key))) {
            cache.put(new Element(key, newValue));
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<K> keySet() {
        return new KeySetAdapter<K>(cache.getKeys());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return getAll(keySet()).entrySet();
    }
    
    /**
     * Represents the list of keys as a set, which is guaranteed to be true by {@link Ehcache#getKeys()}'s contract.
     */
    private static final class KeySetAdapter<K> implements Set<K> {
        private final List<K> keys;
        
        public KeySetAdapter(List<K> keys) {
            this.keys = keys;
        }
        public void add(int index, K element) {
            keys.add(index, element);
        }
        public boolean add(K o) {
            return keys.add(o);
        }
        public boolean addAll(Collection<? extends K> c) {
            return keys.addAll(c);
        }
        public boolean addAll(int index, Collection<? extends K> c) {
            return keys.addAll(index, c);
        }
        public void clear() {
            keys.clear();
        }
        public boolean contains(Object o) {
            return keys.contains(o);
        }
        public boolean containsAll(Collection<?> c) {
            return keys.containsAll(c);
        }
        public boolean equals(Object o) {
            return keys.equals(o);
        }
        public K get(int index) {
            return keys.get(index);
        }
        public int hashCode() {
            return keys.hashCode();
        }
        public int indexOf(Object o) {
            return keys.indexOf(o);
        }
        public boolean isEmpty() {
            return keys.isEmpty();
        }
        public Iterator<K> iterator() {
            return keys.iterator();
        }
        public int lastIndexOf(Object o) {
            return keys.lastIndexOf(o);
        }
        public ListIterator<K> listIterator() {
            return keys.listIterator();
        }
        public ListIterator<K> listIterator(int index) {
            return keys.listIterator(index);
        }
        public K remove(int index) {
            return keys.remove(index);
        }
        public boolean remove(Object o) {
            return keys.remove(o);
        }
        public boolean removeAll(Collection<?> c) {
            return keys.removeAll(c);
        }
        public boolean retainAll(Collection<?> c) {
            return keys.retainAll(c);
        }
        public K set(int index, K element) {
            return keys.set(index, element);
        }
        public int size() {
            return keys.size();
        }
        public List<K> subList(int fromIndex, int toIndex) {
            return keys.subList(fromIndex, toIndex);
        }
        public Object[] toArray() {
            return keys.toArray();
        }
        public <T> T[] toArray(T[] a) {
            return keys.toArray(a);
        }
    }
}
