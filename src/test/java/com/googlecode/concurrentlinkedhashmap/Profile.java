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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.twitter.jsr166e.LongAdder;

/**
 * A simple bootstrap for inspecting with an attached profiler.
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
public final class Profile {
  static final int NUM_THREADS = 25;
  static final int DISPLAY_DELAY_SEC = 5;
  static final LongAdder calls = new LongAdder();

  public static void main(String[] args) throws Exception {
    final ConcurrentLinkedHashMap<Long, Long> map = new Builder<Long, Long>()
        .maximumWeightedCapacity(NUM_THREADS)
        .build();
    scheduleStatusTask();
    ConcurrentTestHarness.timeTasks(25, new Runnable() {
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

  static void scheduleStatusTask() {
    Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
      final Stopwatch stopwatch = new Stopwatch().start();
      @Override public void run() {
        long count = calls.longValue();
        long rate = count / stopwatch.elapsedTime(TimeUnit.SECONDS);
        System.out.printf("%s - %,d [%,d / sec]\n", stopwatch, count, rate);
      }
    }, DISPLAY_DELAY_SEC, DISPLAY_DELAY_SEC, TimeUnit.SECONDS);
  }
}
