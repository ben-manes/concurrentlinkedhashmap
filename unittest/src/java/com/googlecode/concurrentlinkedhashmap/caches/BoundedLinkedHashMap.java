package com.googlecode.concurrentlinkedhashmap.caches;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bounded {@link LinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class BoundedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
  private static final long serialVersionUID = 1L;
  private final int maximumCapacity;

  enum AccessOrder {
    FIFO(false), LRU(true);

    final boolean accessOrder;
    private AccessOrder(boolean accessOrder) {
      this.accessOrder = accessOrder;
    }
    boolean get() {
      return accessOrder;
    }
  }

  public BoundedLinkedHashMap(AccessOrder accessOrder, CacheBuilder builder) {
    super(builder.initialCapacity, 0.75f, accessOrder.get());
    this.maximumCapacity = builder.maximumCapacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maximumCapacity;
  }
}
