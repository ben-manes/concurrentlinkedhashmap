package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A unit-test to assert basic concurrency characteristics by validating the
 * internal state after load.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class MultiThreadedTest extends BaseTest {
  private Queue<String> failures;
  private List<Integer> keys;
  private int timeout = 180;
  private int nThreads;

  @Override
  protected int capacity() {
    return intProperty("multiThreaded.maximumCapacity");
  }

  @BeforeClass(alwaysRun = true)
  public void beforeMultiThreaded() {
    int iterations = intProperty("multiThreaded.iterations");
    nThreads = intProperty("multiThreaded.nThreads");
    timeout = intProperty("multiThreaded.timeout");
    failures = new ConcurrentLinkedQueue<String>();
    keys = new ArrayList<Integer>();
    Random random = new Random();
    for (int i = 0; i < iterations; i++) {
      keys.add(random.nextInt(iterations / 100));
    }
  }

  /**
   * Tests that the cache does not have a memory leak by not being able to
   * drain the eviction queues fast enough.
   */
  @Test(groups = "load")
  public void memoryLeak() throws InterruptedException {
    final String WARNING = "WARNING: This test will run forever and must be manually stopped";
    final int ITERATIONS = 100000;
    final int NUM_THREADS = 1000;
    info(WARNING);

    class Listener implements EvictionListener<Integer, Integer> {
      ConcurrentLinkedHashMap<?, ?> cache;
      volatile int calls;

      @Override
      public void onEviction(Integer key, Integer value) {
        calls++;

        if ((calls % ITERATIONS) == 0) {
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
    Listener listener = new Listener();

    final ConcurrentLinkedHashMap<Integer, Integer> cache =
        new Builder<Integer, Integer>()
            .maximumWeightedCapacity(1000)
            .concurrencyLevel(NUM_THREADS)
            .initialCapacity(ITERATIONS)
            .listener(listener)
            .build();
    listener.cache = cache;

    timeTasks(1000, new Runnable() {
      @Override
      public void run() {
        int current = (int) Math.random();
        for (int i=1; ; i++) {
          cache.put(current, current);
          cache.get(current);
          current++;
          if ((i % ITERATIONS) == 0) {
            debug("Write queue size = " + cache.writeQueue.size());
            info(WARNING);
          }
        }
      }
    });
  }

  /**
   * Tests that the cache is in the correct test after a read-write load.
   */
  @Test(groups = "development")
  public void concurrent() throws InterruptedException {
    final List<List<Integer>> sets = shuffle(nThreads, keys);
    ThreadPoolExecutor es =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                               new LinkedBlockingQueue<Runnable>());
    final ConcurrentLinkedHashMap<Integer, Integer> cache =
        new Builder<Integer, Integer>()
            .maximumWeightedCapacity(capacity())
            .concurrencyLevel(nThreads)
            .build();
    Future<Long> future = es.submit(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        return timeTasks(nThreads, new Thrasher(cache, sets));
      }
    });
    try {
      long timeNS = future.get(timeout, TimeUnit.SECONDS);
      debug("\nExecuted in %d second(s)", TimeUnit.NANOSECONDS.toSeconds(timeNS));
      validator.checkValidState(cache);
    } catch (ExecutionException e) {
      fail("Exception during test: " + e.toString(), e);
    } catch (TimeoutException e) {
      for (StackTraceElement[] trace : Thread.getAllStackTraces().values()) {
        for (StackTraceElement element : trace) {
          info("\tat " + element);
        }
        if (trace.length > 0) {
          info("------");
        }
      }
      es.shutdownNow();
      while (es.getPoolSize() > 0) { /* spin until terminated */ }

      // Print the state of the cache
      debug("Cached Elements: %s", cache.toString());
      debug("List Forward:\n%s", listForwardToString(cache));
      debug("List Backward:\n%s", listBackwardsToString(cache));

      // Print the recorded failures
      for (String failure : failures) {
        debug(failure);
      }
      fail("Spun forever", e);
    }
    debug("concurrent: END");
  }

  /**
   * Based on the passed in working set, creates N shuffled variants.
   *
   * @param samples the number of variants to create
   * @param workingSet the base working set to build from
   */
  private <T> List<List<T>> shuffle(int samples, Collection<T> workingSet) {
    List<List<T>> sets = new ArrayList<List<T>>(samples);
    for (int i = 0; i < samples; i++) {
      List<T> set = new ArrayList<T>(workingSet);
      Collections.shuffle(set);
      sets.add(set);
    }
    return sets;
  }

  /**
   * Executes operations against the cache to simulate random load.
   */
  private final class Thrasher implements Runnable {
    private final ConcurrentLinkedHashMap<Integer, Integer> cache;
    private final List<List<Integer>> sets;
    private final AtomicInteger index;

    public Thrasher(ConcurrentLinkedHashMap<Integer, Integer> cache, List<List<Integer>> sets) {
      this.index = new AtomicInteger();
      this.cache = cache;
      this.sets = sets;
    }

    @Override
    public void run() {
      Operation[] ops = Operation.values();
      int id = index.getAndIncrement();
      Random random = new Random();
      debug("#%d: STARTING", id);
      for (Integer key : sets.get(id)) {
        Operation operation = ops[random.nextInt(ops.length)];
        try {
          operation.execute(cache, key);
        } catch (RuntimeException e) {
          String error =
              String.format("Failed: key %s on operation %s for node %s", key, operation,
                            nodeToString(findNode(key, cache)));
          failures.add(error);
          throw e;
        } catch (Throwable thr) {
          String error =
              String.format("Halted: key %s on operation %s for node %s", key, operation,
                            nodeToString(findNode(key, cache)));
          failures.add(error);
        }
      }
      debug("#%d: ENDING", id);
    }
  }

  /**
   * The public operations that can be performed on the cache.
   */
  private enum Operation {
    CONTAINS_KEY() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.containsKey(key);
      }
    },
    CONTAINS_VALUE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.containsValue(key);
      }
    },
    IS_EMPTY() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.isEmpty();
      }
    },
    SIZE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        if (cache.size() < 0) {
          throw new IllegalStateException();
        }
      }
    },
    WEIGHTED_SIZE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        if (cache.weightedSize() < 0) {
          throw new IllegalStateException();
        }
      }
    },
    CAPACITY() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.setCapacity(cache.capacity());
      }
    },
    GET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.get(key);
      }
    },
    PUT() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.put(key, key);
      }
    },
    PUT_IF_ABSENT() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.putIfAbsent(key, key);
      }
    },
    REMOVE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.remove(key);
      }
    },
    REMOVE_IF_EQUAL() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.remove(key, key);
      }
    },
    REPLACE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.replace(key, key);
      }
    },
    REPLACE_IF_EQUAL() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.replace(key, key, key);
      }
    },
    CLEAR() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.clear();
      }
    },
    KEY_SET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Integer i : cache.keySet()) {
          if (i == null) {
            throw new IllegalArgumentException();
          }
        }
        cache.keySet().toArray(new Integer[cache.size()]);
      }
    },
    VALUE_SET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Integer i : cache.values()) {
          if (i == null) {
            throw new IllegalArgumentException();
          }
        }
        cache.values().toArray(new Integer[cache.size()]);
      }
    },
    ENTRY_SET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Entry<Integer, Integer> entry : cache.entrySet()) {
          if ((entry == null) || (entry.getKey() == null) || (entry.getValue() == null)) {
            throw new IllegalArgumentException(String.valueOf(entry));
          }
        }
        cache.entrySet().toArray(new Entry[cache.size()]);
      }
    },
    HASHCODE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.hashCode();
      }
    },
    EQUALS() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.equals(cache);
      }
    },
    TO_STRING() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.toString();
      }
    },
    SERIALIZE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        SerializationUtils.clone(cache);
      }
    };

    /**
     * Executes the operation.
     *
     * @param cache the cache to operate against
     * @param key the key to perform the operation with
     */
    abstract void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key);
  }
}
