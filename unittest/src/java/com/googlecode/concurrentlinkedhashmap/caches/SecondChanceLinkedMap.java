package com.googlecode.concurrentlinkedhashmap.caches;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.SetUtils;
import org.apache.commons.collections.Transformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ConcurrentMap} that evicts elements once the capacity is reached.
 * <p/>
 * A second-chance FIFO algorithm is used to achieve a hit rate comparable to an LRU without the
 * overhead of maintaining the access order. In order to avoid added operations against the data
 * store, key-value relationships are maintained through links.
 * <p/>
 * <b>Note: This was the 3rd prototype of a fast caching algorithm.</b>
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
final class SecondChanceLinkedMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<EntryKey<K, V>, EntryValue<K, V>> map;
  private final Queue<EntryKey<K, V>> queue;
  private final AtomicInteger capacity;
  private final AtomicInteger size;

  /**
   * A self-evicting map that is bounded to a maximum capacity.
   *
   * @param capacity The maximum size that the map can grow to.
   */
  public SecondChanceLinkedMap(int capacity) {
    this(capacity, 25);
  }

  /**
   * A self-evicting map that is bounded to a maximum capacity.
   *
   * @param capacity         The maximum size that the map can grow to.
   * @param concurrencyLevel The estimated number of concurrently updating threads.
   */
  public SecondChanceLinkedMap(int capacity, int concurrencyLevel) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("The capacity must be positive");
    }
    this.map =
        new ConcurrentHashMap<EntryKey<K, V>, EntryValue<K, V>>(capacity, 0.75f, concurrencyLevel);
    this.queue = new ConcurrentLinkedQueue<EntryKey<K, V>>();
    this.capacity = new AtomicInteger(capacity);
    this.size = new AtomicInteger();
  }

  /**
   * Sets the maximum capacity of the map and eagerly evicts entries until the queue shrinks to
   * the appropriate size.
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
   * Retrieves the maximum capacity of the queue.
   *
   * @return The queue's capacity.
   */
  public int capacity() {
    return capacity.get();
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
  public boolean isEmpty() {
    return size.get() == 0;
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
    return map.containsKey(new EntryKey(key));
  }

  /**
   * {@inheritDoc}
   */
  public boolean containsValue(Object value) {
    return map.containsValue(new EntryValue(value));
  }

  /**
   * Evicts a single entry from the map if it exceeds capacity.
   */
  private void evict() {
    while (size() > capacity()) {
      EntryKey<K, V> key = queue.poll();
      if (key == null) {
        return;
      }
      EntryValue<K, V> value = key.getValue();
      if (value != null) {
        if (value.saved) {
          value.saved = false;
          queue.offer(key);
        } else if (map.remove(key) != null) {
          size.decrementAndGet();
          return;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public V get(Object key) {
    EntryValue<K, V> value = map.get(new EntryKey(key));
    if (value != null) {
      value.saved = true;
      return value.innerValue;
    }
    return null;
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
    EntryKey<K, V> entryKey = new EntryKey<K, V>(key);
    EntryValue<K, V> entryValue = new EntryValue<K, V>(entryKey, value);
    entryKey.setValue(entryValue);

    EntryValue<K, V> old = map.put(entryKey, entryValue);
    if (old == null) { // added
      queue.offer(entryKey);
      size.incrementAndGet();
      evict();
      return null;
    }
    // replaced
    old.getKey().setValue(entryValue);
    entryValue.setKey(old.getKey());
    entryValue.saved = true;
    return old.innerValue;
  }

  /**
   * {@inheritDoc}
   */
  public V putIfAbsent(K key, V value) {
    EntryKey<K, V> entryKey = new EntryKey<K, V>(key);
    EntryValue<K, V> entryValue = new EntryValue<K, V>(entryKey, value);
    entryKey.setValue(entryValue);

    EntryValue<K, V> old = map.putIfAbsent(entryKey, entryValue);
    if (old == null) { // succeeded
      queue.offer(entryKey);
      size.incrementAndGet();
      evict();
      return null;
    }
    // failed
    return old.innerValue;
  }

  /**
   * {@inheritDoc}
   */
  public V remove(Object key) {
    EntryKey<?, ?> entryKey = new EntryKey(key);
    EntryValue<K, V> old = map.remove(entryKey);
    if (old != null) {
      size.decrementAndGet();
      queue.remove(entryKey);
      return old.innerValue;
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean remove(Object key, Object value) {
    EntryKey<?, ?> entryKey = new EntryKey(key);
    EntryValue<K, V> entryValue = map.get(entryKey);
    if ((entryValue != null) && entryValue.innerValue.equals(value)) {
      if (map.remove(entryKey) != null) {
        size.decrementAndGet();
        queue.remove(entryKey);
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public V replace(K key, V value) {
    EntryKey<K, V> entryKey = new EntryKey<K, V>(key);
    EntryValue<K, V> entryValue = new EntryValue<K, V>(entryKey, value);

    EntryValue<K, V> old = map.replace(entryKey, entryValue);
    if (old != null) {
      old.getKey().setValue(entryValue);
      return old.innerValue;
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean replace(K key, V oldValue, V newValue) {
    EntryValue<K, V> old = map.get(new EntryKey<K, V>(key));
    if ((old != null) && (old.innerValue.equals(oldValue))) {
      EntryKey<K, V> entryKey = old.getKey();
      entryKey.setValue(new EntryValue<K, V>(entryKey, newValue));
      return true;
    }
    return false;
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
  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public Set<K> keySet() {
    return Collections.<K>unmodifiableSet(SetUtils.transformedSet(
        new HashSet<EntryKey<K, V>>(map.keySet()), new KeyTransformer()));
  }

  /**
   * {@inheritDoc}
   */
  public Collection<V> values() {
    return Collections.<V>unmodifiableCollection(
        CollectionUtils.transformedCollection(
            new ArrayList<EntryValue<K, V>>(map.values()), new ValueTransformer()));
  }

  /**
   * {@inheritDoc}
   */
  public Set<Entry<K, V>> entrySet() {
    Map<K, V> transformed =
        MapUtils.transformedMap(new HashMap<EntryKey<K, V>, EntryValue<K, V>>(map),
                                new KeyTransformer(), new ValueTransformer());
    return Collections.unmodifiableSet(transformed.entrySet());
  }

  /**
   * The key for an entry in the data store. It maintains a weak reference to the associated
   * value. This value should be checked for <tt>null</tt> to determine whether it still exists
   * within the data store.
   */
  private static final class EntryKey<K, V> {

    final K innerKey;
    volatile EntryValue<K, V> value;

    /**
     * Creates a key where the value reference has not been set. This constructor can be used
     * for looking up entries in the data store or, if the value is specified afterwards, for
     * storing into the data store.
     */
    public EntryKey(K key) {
      this.innerKey = key;
    }

    public EntryValue<K, V> getValue() {
      return value;
    }

    public void setValue(EntryValue<K, V> value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      return innerKey.equals(((EntryKey<?, ?>) obj).innerKey);
    }

    @Override
    public int hashCode() {
      return innerKey.hashCode();
    }

    @Override
    public String toString() {
      return innerKey.toString();
    }
  }

  /**
   * The value for an entry in the data store. It maintains a weak reference to the associated
   * key. The "saved" field marks whether this value was accessed since it was last reset.
   */
  private static final class EntryValue<K, V> {

    final V innerValue;
    volatile boolean saved;
    volatile EntryKey<K, V> key;

    /**
     * Creates a value where the key reference has not been set. This constructor can be used
     * for looking up entries in the data store or, if the key is specified afterwards, for
     * storing into the data store.
     */
    public EntryValue(V value) {
      this.innerValue = value;
    }

    public EntryValue(EntryKey<K, V> key, V value) {
      this(value);
      setKey(key);
    }

    public EntryKey<K, V> getKey() {
      return key;
    }

    public void setKey(EntryKey<K, V> key) {
      this.key = key;
    }

    @Override
    public boolean equals(Object obj) {
      return innerValue.equals(((EntryValue<?, ?>) obj).innerValue);
    }

    @Override
    public int hashCode() {
      return innerValue.hashCode();
    }

    @Override
    public String toString() {
      return innerValue.toString();
    }
  }

  /**
   * Unwraps the key from the {@link EntryKey} for external usage.
   */
  private static final class KeyTransformer implements Transformer {

    public Object transform(Object key) {
      return ((SecondChanceLinkedMap.EntryKey<?, ?>) key).innerKey;
    }
  }

  /**
   * Unwraps the value from the {@link EntryValue} for external usage.
   */
  private static final class ValueTransformer implements Transformer {

    public Object transform(Object value) {
      return ((SecondChanceLinkedMap.EntryValue<?, ?>) value).innerValue;
    }
  }
}
