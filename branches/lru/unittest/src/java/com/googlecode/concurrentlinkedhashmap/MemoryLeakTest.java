package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A unit-test to assert that the cache does not have a memory leak by not being
 * able to drain the eviction queues fast enough.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "load")
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
          listener.cache.put(current, current);
          listener.cache.get(current);
          current++;
          if ((i % ITERATIONS) == 0) {
            debug("Write queue size = " + listener.cache.writeQueue.size());
            info(WARNING);
          }
        }
      }
    });
  }

  final class Listener implements EvictionListener<Integer, Integer> {
    final ConcurrentLinkedHashMap<Integer, Integer> cache;
    final AtomicInteger calls;

    Listener() {
      calls = new AtomicInteger();
      cache = new Builder<Integer, Integer>()
          .maximumWeightedCapacity(1000)
          .concurrencyLevel(NUM_THREADS)
          .initialCapacity(ITERATIONS)
          .listener(this)
          .build();
    }

    @Override
    public void onEviction(Integer key, Integer value) {
      calls.incrementAndGet();

      if ((calls.get() % ITERATIONS) == 0) {
        long reorders = 0;
        for (Queue<?> queue : cache.recencyQueue) {
          reorders += queue.size();
        }
        debug("Evicted by thread #" + Thread.currentThread().getId());
        debug("Write queue size = " + cache.writeQueue.size());
        debug("Read queues size = " + reorders);
        info(WARNING);
      }
    }
  }
}
