package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A unit-test to assert that the cache does not have a memory leak by not being
 * able to drain the recency queues fast enough.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "memoryLeak")
public final class MemoryLeakTest {
  private static final long DAY = 86400000;
  private static final long SECONDS = 1000;

  private static final long TIME_OUT = 1 * DAY;
  private static final long STATUS_INTERVAL = 5 * SECONDS;

  private static final int THREADS = 250;

  private ConcurrentLinkedHashMap<Long, Long> map;
  private ScheduledExecutorService es;

  @BeforeMethod
  public void beforeMemoryLeakTest() {
    map = new Builder<Long, Long>()
        .maximumWeightedCapacity(THREADS)
        .build();
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setPriority(Thread.MAX_PRIORITY)
        .setDaemon(true)
        .build();
    es = Executors.newSingleThreadScheduledExecutor(threadFactory);
    es.scheduleAtFixedRate(newStatusTask(),
        STATUS_INTERVAL, STATUS_INTERVAL, TimeUnit.MILLISECONDS);
  }

  @AfterMethod
  public void afterMemoryLeakTest() {
    es.shutdownNow();
  }

  @Test(timeOut = TIME_OUT)
  public void memoryLeak() throws InterruptedException {
    timeTasks(THREADS, new Runnable() {
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
      int runningTime;

      @Override public void run() {
        long pending = 0;
        for (int i = 0; i < map.recencyQueue.length; i++) {
          pending += map.recencyQueueLength.get(i);
        }
        runningTime += STATUS_INTERVAL;
        String elapsedTime = DurationFormatUtils.formatDuration(runningTime, "H:mm:ss");
        String pendingReads = NumberFormat.getInstance().format(pending);
        System.out.printf("---------- %s ----------\n", elapsedTime);
        System.out.printf("Pending recencies = %s\n", pendingReads);
      }
    };
  }
}
