package com.googlecode.concurrentlinkedhashmap.caches;

import java.util.concurrent.ConcurrentMap;

/**
 * A builder that creates bounded map instances. It provides a flexible approach
 * for constructing different cache data structures with a named parameter
 * syntax.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class CacheBuilder {
  static final int DEFAULT_CONCURRENCY_LEVEL = 16;

  int concurrencyLevel;
  int initialCapacity;
  int maximumCapacity;

  public CacheBuilder() {
    maximumCapacity = -1;
    concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;
  }

  /**
   * Specifies the initial capacity of the hash table (default <tt>16</tt>).
   * This is the number of key-value pairs that the hash table can hold
   * before a resize operation is required.
   *
   * @param initialCapacity the initial capacity used to size the hash table
   *     to accommodate this many entries.
   * @throws IllegalArgumentException if the initialCapacity is negative
   */
  public CacheBuilder initialCapacity(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException();
    }
    this.initialCapacity = initialCapacity;
    return this;
  }

  /**
   * Specifies the maximum capacity to coerces the map to and may exceed it
   * temporarily.
   *
   * @param maximumCapacity the threshold to bound the map by
   * @throws IllegalArgumentException if the maximumCapacity is negative
   */
  public CacheBuilder maximumCapacity(int maximumCapacity) {
    if (maximumCapacity < 0) {
      throw new IllegalArgumentException();
    }
    this.maximumCapacity = maximumCapacity;
    return this;
  }

  /**
   * Specifies the estimated number of concurrently updating threads. The
   * implementation performs internal sizing to try to accommodate this many
   * threads (default <tt>16</tt>).
   *
   * @param concurrencyLevel the estimated number of concurrently updating
   *     threads
   * @throws IllegalArgumentException if the concurrencyLevel is less than or
   *     equal to zero
   */
  public CacheBuilder concurrencyLevel(int concurrencyLevel) {
    if (concurrencyLevel <= 0) {
      throw new IllegalArgumentException();
    }
    this.concurrencyLevel = concurrencyLevel;
    return this;
  }

  /**
   * Creates a new cache instance.
   *
   * @param cache the type of cache to construct
   * @throws IllegalStateException if the maximum weighted capacity was
   *     not set
   */
  public <K, V> ConcurrentMap<K, V> makeCache(Cache cache) {
    if (maximumCapacity < 0) {
      throw new IllegalStateException();
    }
    return cache.create(this);
  }
}
