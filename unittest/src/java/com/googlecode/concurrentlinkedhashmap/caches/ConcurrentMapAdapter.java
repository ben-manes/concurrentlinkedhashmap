package com.googlecode.concurrentlinkedhashmap.caches;

import com.google.common.collect.ForwardingMap;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * A forwarding map that adapts the delegate to the {@link ConcurrentMap}
 * interface. The adaption is not thread-safe.
 *
 * @author bmanes@google.com (Ben Manes)
 */
final class ConcurrentMapAdapter<K, V> extends ForwardingMap<K, V> implements ConcurrentMap<K, V> {
  private final Map<K, V> delegate;

  ConcurrentMapAdapter(Map<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V putIfAbsent(K key, V value) {
    V currentValue = get(key);
    return (currentValue == null)
        ? put(key, value)
        : currentValue;
  }

  @Override
  public boolean remove(Object key, Object value) {
    if (value.equals(get(key))) {
      remove(key);
      return true;
    }
    return false;
  }

  @Override
  public V replace(K key, V value) {
    return containsKey(key)
        ? put(key, value)
        : null;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    V currentValue = get(key);
    if (oldValue.equals(currentValue)) {
      put(key, newValue);
      return true;
    }
    return false;
  }

  @Override
  protected Map<K, V> delegate() {
    return delegate;
  }
}
