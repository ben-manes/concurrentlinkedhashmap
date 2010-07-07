package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newSetFromMap;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;
import static com.googlecode.concurrentlinkedhashmap.ValidState.valid;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.shuffle;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    Random random = new Random();
    keys = newArrayList();
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
      AtomicInteger calls = new AtomicInteger();
      ConcurrentLinkedHashMap<?, ?> cache;

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
        for (int i = 1;; i++) {
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

  /** Provides the map, working sets, and executor for the concurrency test. */
  @DataProvider(name = "concurrentTestProvider")
  public Object[][] provideForConcurrentTest() {
    ConcurrentLinkedHashMap<?, ?> map = new Builder<Object, Object>()
        .maximumWeightedCapacity(capacity())
        .concurrencyLevel(nThreads)
        .build();
    List<List<Integer>> sets = shuffle(nThreads, keys);
    ExecutorService es = Executors.newSingleThreadExecutor();
    return new Object[][] {{ map, sets, es }};
  }

  /** Tests that the map is in a valid state after a read-write load. */
  @Test(groups = "development", dataProvider = "concurrentTestProvider")
  public void concurrent(final ConcurrentLinkedHashMap<Integer, Integer> map,
      final List<List<Integer>> sets, ExecutorService es) throws InterruptedException {
    Future<Long> future = es.submit(new Callable<Long>() {
      @Override public Long call() throws Exception {
        return timeTasks(nThreads, new Thrasher(map, sets));
      }
    });
    try {
      long timeNS = future.get(timeout, TimeUnit.SECONDS);
      debug("\nExecuted in %d second(s)", TimeUnit.NANOSECONDS.toSeconds(timeNS));
      assertThat(map, is(valid()));
    } catch (ExecutionException e) {
      fail("Exception during test: " + e.toString(), e);
    } catch (TimeoutException e) {
      handleTimout(es, e, map);
    }
  }

  private void handleTimout(ExecutorService es, TimeoutException e,
      ConcurrentLinkedHashMap<Integer, Integer> cache) throws InterruptedException {
    for (StackTraceElement[] trace : Thread.getAllStackTraces().values()) {
      for (StackTraceElement element : trace) {
        info("\tat " + element);
      }
      if (trace.length > 0) {
        info("------");
      }
    }
    es.shutdownNow();
    es.awaitTermination(10, TimeUnit.SECONDS);

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
              String.format("Failed: key %s on operation %s for node %s",
                  key, operation, nodeToString(findNode(key, cache)));
          failures.add(error);
          throw e;
        } catch (Throwable thr) {
          String error =
              String.format("Halted: key %s on operation %s for node %s",
                  key, operation, nodeToString(findNode(key, cache)));
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

  /* ---------------- List/Node utilities -------------- */

  protected static String listForwardToString(ConcurrentLinkedHashMap<?, ?> map) {
    return listToString(map, true);
  }

  protected static String listBackwardsToString(ConcurrentLinkedHashMap<?, ?> map) {
    return listToString(map, false);
  }

  private static String listToString(ConcurrentLinkedHashMap<?, ?> map, boolean forward) {
    map.evictionLock.lock();
    try {
      Set<Object> seen = newSetFromMap(new IdentityHashMap<Object, Boolean>());
      StringBuilder buffer = new StringBuilder("\n");
      ConcurrentLinkedHashMap<?, ?>.Node current = forward ? map.sentinel.next : map.sentinel.prev;
      while (current != map.sentinel) {
        buffer.append(nodeToString(current)).append("\n");
        if (seen.add(current)) {
          buffer.append("Failure: Loop detected\n");
          break;
        }
        current = forward ? current.next : current.next.prev;
      }
      return buffer.toString();
    } finally {
      map.evictionLock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static String nodeToString(Node node) {
    if (node == null) {
      return "null";
    } else if (node.segment == -1) {
      return "setinel";
    }
    return node.key + "=" + node.weightedValue.value;
  }

  /** Finds the node in the map by walking the list. Returns null if not found. */
  protected static ConcurrentLinkedHashMap<?, ?>.Node findNode(
      Object key, ConcurrentLinkedHashMap<?, ?> map) {
    map.evictionLock.lock();
    try {
      ConcurrentLinkedHashMap<?, ?>.Node current = map.sentinel;
      while (current != map.sentinel) {
        if (current.equals(key)) {
          return current;
        }
      }
      return null;
    } finally {
      map.evictionLock.unlock();
    }
  }
}
