/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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
package com.googlecode.concurrentlinkedhashmap.caches;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import static net.sf.ehcache.Cache.DEFAULT_CACHE_NAME;

/**
 * Exposes <tt>ehcache</tt> as a {@link ConcurrentMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EhcacheMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
  private final Ehcache cache;

  public EhcacheMap(MemoryStoreEvictionPolicy evictionPolicy, CacheFactory builder) {
    CacheConfiguration config = new CacheConfiguration(DEFAULT_CACHE_NAME, builder.maximumCapacity);
    config.setMemoryStoreEvictionPolicyFromObject(evictionPolicy);
    CacheManager cacheManager = new CacheManager();
    cache = new Cache(config);
    cache.setCacheManager(cacheManager);
    cache.initialise();
  }

  @Override
  public void clear() {
    cache.removeAll();
  }

  @Override
  public int size() {
    return cache.getSize();
  }

  @Override
  public boolean containsKey(Object key) {
    return cache.isKeyInCache(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return cache.isValueInCache(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(Object key) {
    Element element = cache.get(key);
    return (element == null) ? null : (V) element.getObjectValue();
  }

  @SuppressWarnings("unchecked")
  public Map<K, V> getAll(Collection<? extends K> keys) {
    Map<Object, Element> cached = cache.getAll(keys);
    Map<K, V> results = new HashMap<K, V>(cached.size());
    for (Element element : cached.values()) {
      results.put((K) element.getObjectKey(), (V) element.getObjectValue());
    }
    return results;
  }

  @Override
  public V put(K key, V value) {
    V old = get(key);
    cache.put(new Element(key, value));
    return old;
  }

  @Override
  @SuppressWarnings("unchecked")
  public V putIfAbsent(K key, V value) {
    Element old = cache.putIfAbsent(new Element(key, value), true);
    return (old == null) ? null : (V) old.getObjectValue();
  }

  @Override
  public V remove(Object key) {
    V old = get(key);
    if (old != null) {
      cache.remove(key);
    }
    return old;
  }

  @Override
  public boolean remove(Object key, Object value) {
    return cache.removeElement(new Element(key, value));
  }

  @Override
  @SuppressWarnings("unchecked")
  public V replace(K key, V value) {
    Element old = cache.replace(new Element(key, value));
    return (old == null) ? null : (V) old.getObjectValue();
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return cache.replace(new Element(key, oldValue), new Element(key, newValue));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<K> keySet() {
    return new KeySetAdapter<K>(cache.getKeys());
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return getAll(keySet()).entrySet();
  }

  /**
   * Represents the list of keys as a set, which is guaranteed to be true by
   * {@link Ehcache#getKeys()}'s contract.
   */
  private static final class KeySetAdapter<K> implements Set<K> {
    private final List<K> keys;

    public KeySetAdapter(List<K> keys) {
      this.keys = keys;
    }
    @Override public boolean add(K o) {
      return keys.add(o);
    }
    @Override public boolean addAll(Collection<? extends K> c) {
      return keys.addAll(c);
    }
    @Override public void clear() {
      keys.clear();
    }
    @Override public boolean contains(Object o) {
      return keys.contains(o);
    }
    @Override public boolean containsAll(Collection<?> c) {
      return keys.containsAll(c);
    }
    @Override public boolean isEmpty() {
      return keys.isEmpty();
    }
    @Override public Iterator<K> iterator() {
      return keys.iterator();
    }
    @Override public boolean remove(Object o) {
      return keys.remove(o);
    }
    @Override public boolean removeAll(Collection<?> c) {
      return keys.removeAll(c);
    }
    @Override public boolean retainAll(Collection<?> c) {
      return keys.retainAll(c);
    }
    @Override public int size() {
      return keys.size();
    }
    @Override public boolean equals(Object o) {
      return keys.equals(o);
    }
    @Override public int hashCode() {
      return keys.hashCode();
    }
    @Override public Object[] toArray() {
      return keys.toArray();
    }
    @Override public <T> T[] toArray(T[] a) {
      return keys.toArray(a);
    }
  }
}
