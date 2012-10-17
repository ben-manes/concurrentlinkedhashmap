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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.Cache.Policy;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.caches.CacheConcurrentLIRS;

import org.cachebench.CacheBenchmarkRunner;
import org.cachebench.CacheWrapper;
import org.cachebench.reportgenerators.ChartGen;
import org.cachebench.reportgenerators.CsvStatisticReportGenerator;
import org.cachebench.reportgenerators.PutGetChartGenerator;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This benchmark evaluates multi-threaded performance.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CacheBenchmark implements CacheWrapper {
  private static final String REPORT_DIR = "target";
  private static int initialCapacity;
  private static int maximumCapacity;
  private static int concurrencyLevel;
  private static Cache cache;
  private static String run;

  private Map<Object, Object> map;

  @Test(groups = "cachebench")
  @Parameters({"cache", "initialCapacity", "maximumCapacity", "concurrencyLevel", "runId"})
  public static void benchmark(String cache, int initialCapacity, int maximumCapacity,
      int concurrencyLevel, String run) throws IOException {
    CacheBenchmark.run = run;
    CacheBenchmark.cache = Cache.valueOf(cache);
    CacheBenchmark.initialCapacity = initialCapacity;
    CacheBenchmark.maximumCapacity = maximumCapacity;
    CacheBenchmark.concurrencyLevel = concurrencyLevel;

    System.setProperty("cacheBenchFwk.cacheWrapperClassName", CacheBenchmark.class.getName());
    System.setProperty("cacheBenchFwk.report.chart", "putget");
    System.setProperty("localOnly", "true");

    CacheBenchmarkRunner.main(new String[] { });

    ChartGen generator = new PutGetChartGenerator();
    generator.setFileNamePrefix(REPORT_DIR + "/" + "cachebench");
    generator.setReportDirectory(REPORT_DIR);
    generator.generateChart();
  }
  
  private static final boolean TEST_CONCURRENT_LIRS = true;

  @Override
  @SuppressWarnings("rawtypes")
  public void init(Map parameters) throws Exception {
      if (TEST_CONCURRENT_LIRS) {
          // to test with the concurrent LIRS cache:
          map = CacheConcurrentLIRS.newInstance(maximumCapacity);
      } else {
          // to test with the ConcurrentLinkedHashMap:
          map = new CacheBuilder()
              .concurrencyLevel(concurrencyLevel)
              .initialCapacity(initialCapacity)
              .maximumCapacity(maximumCapacity)
              .makeCache(cache);
      }
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
        fileName = cache + (isNullOrEmpty(run) ? "" : "-" + run) + ".csv";
      }
      this.output = new File(REPORT_DIR + "/" + fileName);
    }
  }
}
