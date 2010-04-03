/*
 * Copyright 2010 Benjamin Manes
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
package com.googlecode.concurrentlinkedhashmap;

import java.util.Collection;
import java.util.Map;

/**
 * A common set of {@link Weigher} implementations.
 *
 * @author bmanes@gmail.com (Ben Manes)
 * @see <tt>http://code.google.com/p/concurrentlinkedhashmap/</tt>
 */
@SuppressWarnings("unchecked")
public final class Weighers {

  /**
   * A weigher where a value has a weight of <tt>1</tt>. A map bounded with
   * this weigher will evict when the number of key-value pairs exceeds the
   * capacity.
   *
   * @return A weigher where a value takes one unit of capacity.
   */
  public static <V> Weigher<V> singleton() {
    return (Weigher<V>) SingletonWeigher.INSTANCE;
  }

  /**
   * A weigher where the value is a byte array and its weight is the number of
   * bytes. A map bounded with this weigher will evict when the number of bytes
   * exceeds the capacity rather than the number of key-value pairs in the map.
   * This allows for restricting the capacity based on the memory-consumption
   * and is primarily for usage by dedicated caching servers that hold the
   * serialized data.
   *
   * @return A weigher where each byte takes one unit of capacity.
   */
  public static Weigher<byte[]> byteArray() {
    return ByteArrayWeigher.INSTANCE;
  }

  /**
   * A weigher where the value is an {@link Iterable} and its weight is the
   * number of elements. This weigher only should be used when the alternative
   * {@link #collection()} weigher cannot be, as evaluation takes O(n) time. A
   * map bounded with this weigher will evict when the total number of elements
   * exceeds the capacity rather than the number of key-value pairs in the map.
   *
   * @return A weigher where each element takes one unit of capacity.
   */
  public static <E> Weigher<Iterable<E>> iterable() {
    Weigher<?> weigher = IterableWeigher.INSTANCE;
    return (Weigher<Iterable<E>>) weigher;
  }

  /**
   * A weigher where the value is an {@link Collection} and its weight is the
   * number of elements. A map bounded with this weigher will evict when the
   * total number of elements exceeds the capacity rather than the number of
   * key-value pairs in the map.
   *
   * @return A weigher where each element takes one unit of capacity.
   */
  public static <E> Weigher<Collection<E>> collection() {
    Weigher<?> weigher = CollectionWeigher.INSTANCE;
    return (Weigher<Collection<E>>) weigher;
  }

  /**
   * A weigher where the value is an {@link Map} and its weight is the number of
   * entries. A map bounded with this weigher will evict when the total number of
   * entries across all values exceeds the capacity rather than the number of
   * key-value pairs in the map.
   *
   * @return A weigher where each entry takes one unit of capacity.
   */
  public static <A, B> Weigher<Map<A, B>> map() {
    Weigher<?> weigher = MapWeigher.INSTANCE;
    return (Weigher<Map<A, B>>) weigher;
  }

  private enum SingletonWeigher implements Weigher {
    INSTANCE;

    @Override
    public int weightOf(Object value) {
      return 1;
    }
  }

  private enum ByteArrayWeigher implements Weigher<byte[]> {
    INSTANCE;

    @Override
    public int weightOf(byte[] value) {
      return value.length;
    }
  }

  private enum IterableWeigher implements Weigher<Iterable> {
    INSTANCE;

    @Override
    public int weightOf(Iterable values) {
      int size = 0;
      for (Object value : values) {
        size++;
      }
      return size;
    }
  }

  private enum CollectionWeigher implements Weigher<Collection> {
    INSTANCE;

    @Override
    public int weightOf(Collection values) {
      return values.size();
    }
  }

  private enum MapWeigher implements Weigher<Map> {
    INSTANCE;

    @Override
    public int weightOf(Map values) {
      return values.size();
    }
  }
}
