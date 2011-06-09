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

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A forwarding {@link ConcurrentMap} that wraps each call with a mutex.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class SynchronizedForwardingConcurrentMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<K, V> delegate;
  private final Object lock;

  public SynchronizedForwardingConcurrentMap(Map<K, V> delegate) {
    this.delegate = new ConcurrentMapAdapter<K, V>(delegate);
    this.lock = new Object();
  }

  @Override
  public boolean isEmpty() {
    synchronized (lock) {
      return delegate.isEmpty();
    }
  }

  @Override
  public int size() {
    synchronized (lock) {
      return delegate.size();
    }
  }

  @Override
  public void clear() {
    synchronized (lock) {
      delegate.clear();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    synchronized (lock) {
      return delegate.containsKey(key);
    }
  }

  @Override
  public boolean containsValue(Object value) {
    synchronized (lock) {
      return delegate.containsValue(value);
    }
  }

  @Override
  public V get(Object key) {
    synchronized (lock) {
      return delegate.get(key);
    }
  }

  @Override
  public V put(K key, V value) {
    synchronized (lock) {
      return delegate.put(key, value);
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    synchronized (lock) {
      return delegate.putIfAbsent(key, value);
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    synchronized (lock) {
      delegate.putAll(map);
    }
  }

  @Override
  public V remove(Object key) {
    synchronized (lock) {
      return delegate.remove(key);
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    synchronized (lock) {
      return delegate.remove(key, value);
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    synchronized (lock) {
      return delegate.replace(key, oldValue, newValue);
    }
  }

  @Override
  public V replace(K key, V value) {
    synchronized (lock) {
      return delegate.replace(key, value);
    }
  }

  @Override
  public Set<K> keySet() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.keySet());
    }
  }

  @Override
  public Collection<V> values() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.values());
    }
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.entrySet());
    }
  }

  @Override
  public boolean equals(Object object) {
    synchronized (lock) {
      return delegate.equals(object);
    }
  }

  @Override
  public int hashCode() {
    synchronized (lock) {
      return delegate.hashCode();
    }
  }
}
