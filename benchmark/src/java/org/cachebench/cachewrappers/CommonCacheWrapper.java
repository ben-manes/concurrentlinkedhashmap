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
package org.cachebench.cachewrappers;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import org.cachebench.CacheWrapper;

import java.util.List;
import java.util.Map;

/**
 * A common facade to bootstrap a cache.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CommonCacheWrapper implements CacheWrapper {
  private Map<Object, Object> map;
  private int initialCapacity;
  private int maximumCapacity;
  private int concurrencyLevel;
  private Cache cacheType;

  @Override
  @SuppressWarnings("unchecked")
  public void init(Map parameters) throws Exception {
    cacheType = Cache.valueOf(System.getProperty("cacheBenchFwk.cache.type"));
    initialCapacity = Integer.getInteger("cacheBenchFwk.cache.initialCapacity");
    maximumCapacity = Integer.getInteger("cacheBenchFwk.cache.maximumCapacity");
    concurrencyLevel = Integer.getInteger("cacheBenchFwk.cache.concurrencyLevel");

    map = new CacheBuilder()
        .concurrencyLevel(concurrencyLevel)
        .initialCapacity(initialCapacity)
        .maximumCapacity(maximumCapacity)
        .makeCache(cacheType);
  }

  @Override
  public void setUp() throws Exception {}

  @Override
  public void put(List<String> path, Object key, Object value) throws Exception {
    map.put(key, value);
  }

  @Override
  public Object get(List<String> path, Object key) throws Exception {
    return map.get(key);
  }

  @Override
  public void empty() throws Exception {
    map.clear();
  }

  @Override
  public int getNumMembers() {
    return 0;
  }

  @Override
  public String getInfo() {
    return cacheType.isBounded()
         ? "size/capacity: " + map.size() + "/" + maximumCapacity
         : "size: " + map.size();
  }

  @Override
  public Object getReplicatedData(List<String> path, String key) throws Exception {
    return get(path, key);
  }

  @Override
  public Object startTransaction() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void endTransaction(boolean successful) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void tearDown() throws Exception {}
}
