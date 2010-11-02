package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A unit-test to assert that the cache does not have a memory leak by not being
 * able to drain the eviction queues fast enough.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "memoryLeak")
public final class MemoryLeakTest extends BaseTest {
  private static final String WARNING =
    "WARNING: This test will run forever and must be manually stopped";

  private static final int ITERATIONS = 100000;
  private static final int NUM_THREADS = 1000;

  @Override
  protected int capacity() {
    throw new UnsupportedOperationException();
  }

  @Test
  public void memoryLeak() throws InterruptedException {
    info(WARNING);

    timeTasks(1000, new Runnable() {
      final Listener listener = new Listener();

      @Override
      public void run() {
        int current = (int) Math.random();
        for (int i = 1;; i++) {
          listener.map.put(current, current);
          listener.map.get(current);
          current++;
          if ((i % NUM_THREADS) == 0) {
            printStatus(listener.map);
          }
        }
      }
    });
  }

  final class Listener implements EvictionListener<Integer, Integer> {
    final ConcurrentLinkedHashMap<Integer, Integer> map;
    final AtomicInteger calls;

    Listener() {
      calls = new AtomicInteger();
      map = new Builder<Integer, Integer>()
          .maximumWeightedCapacity(1000)
          .concurrencyLevel(NUM_THREADS)
          .initialCapacity(ITERATIONS)
          .listener(this)
          .build();
    }

    @Override
    public void onEviction(Integer key, Integer value) {
      calls.incrementAndGet();

      if ((calls.get() % NUM_THREADS) == 0) {
        debug("Evicted by thread #" + Thread.currentThread().getId());
        printStatus(map);
      }
    }
  }

  void printStatus(ConcurrentLinkedHashMap<?, ?> map) {
    long reorders = 0;
    for (int i = 0; i < map.recencyQueue.length; i++) {
      reorders += map.recencyQueueLength.get(i);
    }
    debug("Write queue size = %d", map.writeQueue.size());
    debug("Read queues size = %d", reorders);
    info(WARNING);
  }
}
