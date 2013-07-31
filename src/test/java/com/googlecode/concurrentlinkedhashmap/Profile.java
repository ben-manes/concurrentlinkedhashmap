/*
 * Copyright 2013 Ben Manes. All Rights Reserved.
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.twitter.jsr166e.LongAdder;

/**
 * A simple bootstrap for inspecting with an attached profiler.
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
public final class Profile {
  static final int NUM_THREADS = 25;
  static final int MAX_SIZE = 2* NUM_THREADS;
  static final int DISPLAY_DELAY_SEC = 5;
  static final int INITIAL_DELAY_SEC = 30;
  static final MapType type = MapType.CLHM;
  static volatile LongAdder calls = new LongAdder();

  public static void main(String[] args) throws Exception {
    scheduleStatusTask();
    final Map<Long, Long> map = makeMap(type);
    ConcurrentTestHarness.timeTasks(NUM_THREADS, new Runnable() {
      @Override public void run() {
        Long id = Thread.currentThread().getId();
        map.put(id, id);
        for (;;) {
          map.get(id);
          calls.increment();
        }
      }
    });
  }

  enum MapType {
    LHM, CHM, CHMv8, CLHM, CACHE_BUILDER
  }

  static Map<Long, Long> makeMap(MapType type) {
    switch (type) {
      case LHM:
        return Collections.synchronizedMap(new LinkedHashMap<Long, Long>(MAX_SIZE, 0.75f, true) {
          private static final long serialVersionUID = 1L;
          @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > MAX_SIZE;
          }
        });
      case CHM:
        return Maps.newConcurrentMap();
      case CHMv8:
        return new ConcurrentHashMapV8<Long, Long>();
      case CLHM:
        return new Builder<Long, Long>().maximumWeightedCapacity(MAX_SIZE).build();
      case CACHE_BUILDER:
        return CacheBuilder.newBuilder()
            .maximumSize(MAX_SIZE)
            .<Long, Long>build().asMap();
      default:
        throw new UnsupportedOperationException();
    }
  }

  static void scheduleStatusTask() {
    Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
      final Stopwatch stopwatch = new Stopwatch();
      @Override public void run() {
        if (!stopwatch.isRunning()) {
          System.out.println("Warmup complete");
          calls = new LongAdder();
          stopwatch.start();
          return;
        }
        long count = calls.longValue();
        long rate = count / stopwatch.elapsedTime(TimeUnit.SECONDS);
        System.out.printf("%s - %,d [%,d / sec]\n", stopwatch, count, rate);
      }
    }, INITIAL_DELAY_SEC, DISPLAY_DELAY_SEC, TimeUnit.SECONDS);
  }
}
