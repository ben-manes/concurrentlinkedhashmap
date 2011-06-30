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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A non-thread safe bounded {@link LinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class BoundedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
  private static final long serialVersionUID = 1L;
  private final int maximumCapacity;

  public enum AccessOrder {
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
    this(accessOrder, builder.initialCapacity, builder.maximumCapacity);
  }

  public BoundedLinkedHashMap(AccessOrder accessOrder, int initialCapacity, int maximumCapacity) {
    super(initialCapacity, 0.75f, accessOrder.get());
    this.maximumCapacity = maximumCapacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maximumCapacity;
  }
}
