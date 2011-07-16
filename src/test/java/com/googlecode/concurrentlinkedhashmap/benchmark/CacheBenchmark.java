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
package com.googlecode.concurrentlinkedhashmap.benchmark;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.Cache.Policy;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;

import org.cachebench.CacheBenchmarkRunner;
import org.cachebench.CacheWrapper;
import org.cachebench.reportgenerators.CsvStatisticReportGenerator;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * This benchmark evaluates multi-threaded performance.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CacheBenchmark implements CacheWrapper {
  private static int initialCapacity;
  private static int maximumCapacity;
  private static int concurrencyLevel;
  private static Cache cache;
  private static String run;

  private Map<Object, Object> map;

  @Test(groups = "cachebench")
  @Parameters({"cache", "initialCapacity", "maximumCapacity", "concurrencyLevel", "runId"})
  public static void benchmark(String cache, int initialCapacity, int maximumCapacity,
      int concurrencyLevel, String run) {
    CacheBenchmark.run = run;
    CacheBenchmark.cache = Cache.valueOf(cache);
    CacheBenchmark.initialCapacity = initialCapacity;
    CacheBenchmark.maximumCapacity = maximumCapacity;
    CacheBenchmark.concurrencyLevel = concurrencyLevel;

    System.setProperty("cacheBenchFwk.cacheWrapperClassName", CacheBenchmark.class.getName());
    System.setProperty("cacheBenchFwk.report.chart", "putget");
    System.setProperty("localOnly", "true");

    CacheBenchmarkRunner.main(new String[] { });
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void init(Map parameters) throws Exception {
    // TODO(bmanes): Remove once migrated to Maven
    cache = Cache.valueOf(System.getProperty("cacheBenchFwk.cache.type"));
    initialCapacity = Integer.getInteger("cacheBenchFwk.cache.initialCapacity");
    maximumCapacity = Integer.getInteger("cacheBenchFwk.cache.maximumCapacity");
    concurrencyLevel = Integer.getInteger("cacheBenchFwk.cache.concurrencyLevel");

    map = new CacheBuilder()
        .concurrencyLevel(concurrencyLevel)
        .initialCapacity(initialCapacity)
        .maximumCapacity(maximumCapacity)
        .makeCache(cache);
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
    return (cache.policy() == Policy.UNBOUNDED)
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

  /** An extension of the report generator to simplify the file name. */
  public static final class CustomCsvStatisticReportGenerator extends CsvStatisticReportGenerator {
    @Override public void setOutputFile(String fileName) {
      if ("-generic-".equals(fileName)) {
        this.output = new File(cache + (run == null ? "" : "-" + run) + ".csv");
      } else {
        super.setOutputFile(fileName);
      }
    }
  }
}
