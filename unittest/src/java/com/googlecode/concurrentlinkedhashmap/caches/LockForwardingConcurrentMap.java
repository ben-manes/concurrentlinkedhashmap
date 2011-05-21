/*
 * Copyright 2011 Benjamin Manes
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
import java.util.concurrent.locks.Lock;

/**
 * A forwarding {@link ConcurrentMap} that wraps each call with a lock.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class LockForwardingConcurrentMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<K, V> delegate;
  private final Lock writeLock;
  private final Lock readLock;

  public LockForwardingConcurrentMap(Lock readLock, Lock writeLock, ConcurrentMap<K, V> delegate) {
    this.writeLock = writeLock;
    this.readLock = readLock;
    this.delegate = delegate;
  }

  public boolean isEmpty() {
    readLock.lock();
    try {
      return delegate.isEmpty();
    } finally {
      readLock.unlock();
    }
  }

  public int size() {
    readLock.lock();
    try {
      return delegate.size();
    } finally {
      readLock.unlock();
    }
  }

  public void clear() {
    writeLock.lock();
    try {
      delegate.clear();
    } finally {
      writeLock.unlock();
    }
  }

  public boolean containsKey(Object key) {
    readLock.lock();
    try {
      return delegate.containsKey(key);
    } finally {
      readLock.unlock();
    }
  }

  public boolean containsValue(Object value) {
    readLock.lock();
    try {
      return delegate.containsValue(value);
    } finally {
      readLock.unlock();
    }
  }

  public V get(Object key) {
    readLock.lock();
    try {
      return delegate.get(key);
    } finally {
      readLock.unlock();
    }
  }

  public V put(K key, V value) {
    writeLock.lock();
    try {
      return delegate.put(key, value);
    } finally {
      writeLock.unlock();
    }
  }

  public V putIfAbsent(K key, V value) {
    writeLock.lock();
    try {
      return delegate.putIfAbsent(key, value);
    } finally {
      writeLock.unlock();
    }
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    writeLock.lock();
    try {
      delegate.putAll(map);
    } finally {
      writeLock.unlock();
    }
  }

  public V remove(Object key) {
    writeLock.lock();
    try {
      return delegate.remove(key);
    } finally {
      writeLock.unlock();
    }
  }

  public boolean remove(Object key, Object value) {
    writeLock.lock();
    try {
      return delegate.remove(key, value);
    } finally {
      writeLock.unlock();
    }
  }

  public boolean replace(K key, V oldValue, V newValue) {
    writeLock.lock();
    try {
      return delegate.replace(key, oldValue, newValue);
    } finally {
      writeLock.unlock();
    }
  }

  public V replace(K key, V value) {
    writeLock.lock();
    try {
      return delegate.replace(key, value);
    } finally {
      writeLock.unlock();
    }
  }

  public Set<K> keySet() {
    readLock.lock();
    try {
      return newLinkedHashSet(delegate.keySet());
    } finally {
      readLock.unlock();
    }
  }

  public Collection<V> values() {
    readLock.lock();
    try {
      return newLinkedHashSet(delegate.values());
    } finally {
      readLock.unlock();
    }
  }

  public Set<Entry<K, V>> entrySet() {
    readLock.lock();
    try {
      return newLinkedHashSet(delegate.entrySet());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean equals(Object object) {
    readLock.lock();
    try {
      return delegate.equals(object);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public int hashCode() {
    readLock.lock();
    try {
      return delegate.hashCode();
    } finally {
      readLock.unlock();
    }
  }
}
