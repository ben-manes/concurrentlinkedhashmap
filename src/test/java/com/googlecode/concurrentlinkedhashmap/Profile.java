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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.googlecode.concurrentlinkedhashmap.caches.CacheFactory;
import com.twitter.jsr166e.LongAdder;

/**
 * A simple bootstrap for inspecting with an attached profiler.
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
public final class Profile {
  static final int WARM_UP_SEC = 5;
  static final int DISPLAY_DELAY_SEC = 5;

  static final int NUM_THREADS = 25;
  static final int MAX_SIZE = 2 * NUM_THREADS;
  static final CacheType type = CacheType.ConcurrentLinkedHashMap;

  volatile LongAdder calls;
  final ConcurrentMap<Long, Long> map;

  Profile() {
    calls = new LongAdder();
    map = new CacheFactory()
        .concurrencyLevel(NUM_THREADS)
        .initialCapacity(MAX_SIZE)
        .maximumCapacity(MAX_SIZE)
        .makeCache(type);
  }

  void run() throws InterruptedException {
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

  void scheduleStatusTask() {
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
        long rate = count / stopwatch.elapsed(TimeUnit.SECONDS);
        System.out.printf("%s - %,d [%,d / sec]\n", stopwatch, count, rate);
      }
    }, WARM_UP_SEC, DISPLAY_DELAY_SEC, TimeUnit.SECONDS);
  }

  public static void main(String[] args) throws Exception {
    Profile profile = new Profile();
    profile.scheduleStatusTask();
    profile.run();
  }
}
