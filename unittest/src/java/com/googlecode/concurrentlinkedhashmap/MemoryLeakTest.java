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
package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * A unit-test to assert that the cache does not have a memory leak by not being
 * able to drain the buffers fast enough.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "memoryLeak")
public final class MemoryLeakTest {
  private final long statusInterval;
  private final int threads;

  private ConcurrentLinkedHashMap<Long, Long> map;
  private ScheduledExecutorService statusExecutor;

  @Parameters({"threads", "statusInterval"})
  public MemoryLeakTest(int threads, long statusInterval) {
    this.statusInterval = SECONDS.toMillis(statusInterval);
    this.threads = threads;
  }

  @BeforeMethod
  public void beforeMemoryLeakTest() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setPriority(Thread.MAX_PRIORITY)
        .setDaemon(true)
        .build();
    statusExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    statusExecutor.scheduleAtFixedRate(newStatusTask(),
      statusInterval, statusInterval, MILLISECONDS);
    map = new Builder<Long, Long>()
        .maximumWeightedCapacity(threads)
        .build();
  }

  @AfterMethod
  public void afterMemoryLeakTest() {
    statusExecutor.shutdownNow();
  }

  @Test
  public void memoryLeak() throws InterruptedException {
    timeTasks(threads, new Runnable() {
      @Override public void run() {
        Long id = Thread.currentThread().getId();
        map.put(id, id);
        for (;;) {
          map.get(id);
          Thread.yield();
        }
      }
    });
  }

  private Runnable newStatusTask() {
    return new Runnable() {
      long runningTime;

      @Override public void run() {
        long pending = 0;
        for (int i = 0; i < map.buffers.length; i++) {
          pending += map.bufferLengths.get(i);
        }
        runningTime += statusInterval;
        String elapsedTime = DurationFormatUtils.formatDuration(runningTime, "H:mm:ss");
        String pendingReads = NumberFormat.getInstance().format(pending);
        System.out.printf("---------- %s ----------\n", elapsedTime);
        System.out.printf("Pending tasks = %s\n", pendingReads);
        System.out.printf("Drain status = %s\n", map.drainStatus);
      }
    };
  }
}
