package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newSetFromMap;
import static com.googlecode.concurrentlinkedhashmap.Benchmarks.shuffle;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;
import static com.googlecode.concurrentlinkedhashmap.IsValidState.valid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
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
@Test(groups = "development")
public final class MultiThreadedTest extends BaseTest {
  private Queue<String> failures;
  private List<Integer> keys;
  private int iterations;
  private int nThreads;
  private int timeout;

  @Override
  protected int capacity() {
    return intProperty("multiThreaded.maximumCapacity");
  }

  @BeforeClass(alwaysRun = true)
  public void beforeMultiThreaded() {
    iterations = intProperty("multiThreaded.iterations");
    nThreads = intProperty("multiThreaded.nThreads");
    timeout = intProperty("multiThreaded.timeout");
    failures = new ConcurrentLinkedQueue<String>();
  }

  @Test(dataProvider = "builder")
  public void weightedConcurrency(Builder<Integer, List<Integer>> builder) {
    final ConcurrentLinkedHashMap<Integer, List<Integer>> map = builder
        .weigher(Weighers.<Integer>list())
        .maximumWeightedCapacity(nThreads)
        .build();
    final Queue<List<Integer>> values = new ConcurrentLinkedQueue<List<Integer>>();
    for (int i = 1; i <= nThreads; i++) {
      Integer[] array = new Integer[i];
      Arrays.fill(array, Integer.MIN_VALUE);
      values.add(Arrays.asList(array));
    }
    executeWithTimeOut(map, new Callable<Long>() {
      @Override public Long call() throws Exception {
        return timeTasks(nThreads, new Runnable() {
          @Override public void run() {
            List<Integer> value = values.poll();
            for (int i = 0; i < iterations; i++) {
              map.put(i % 10, value);
            }
          }
        });
      }
    });
  }

  @Test(dataProvider = "builder")
  public void concurrency(Builder<Integer, Integer> builder) {
    keys = newArrayList();
    Random random = new Random();
    for (int i = 0; i < iterations; i++) {
      keys.add(random.nextInt(iterations / 100));
    }
    final List<List<Integer>> sets = shuffle(nThreads, keys);
    final ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(capacity())
        .concurrencyLevel(nThreads)
        .build();
    executeWithTimeOut(map, new Callable<Long>() {
      @Override public Long call() throws Exception {
        return timeTasks(nThreads, new Thrasher(map, sets));
      }
    });
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
                  key, operation, nodeToString(cache.data.get(key)));
          failures.add(error);
          throw e;
        } catch (Throwable thr) {
          String error =
              String.format("Halted: key %s on operation %s for node %s",
                  key, operation, nodeToString(cache.data.get(key)));
          failures.add(error);
        }
      }
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
        checkState(cache.size() >= 0);
      }
    },
    WEIGHTED_SIZE() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        checkState(cache.weightedSize() >= 0);
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
          checkNotNull(i);
        }
        cache.keySet().toArray(new Integer[cache.size()]);
      }
    },
    VALUES() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Integer i : cache.values()) {
          checkNotNull(i);
        }
        cache.values().toArray(new Integer[cache.size()]);
      }
    },
    ENTRY_SET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Entry<Integer, Integer> entry : cache.entrySet()) {
          checkNotNull(entry);
          checkNotNull(entry.getKey());
          checkNotNull(entry.getValue());
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

  /* ---------------- Utilities -------------- */

  private void executeWithTimeOut(
      ConcurrentLinkedHashMap<?, ?> map, Callable<Long> task) {
    ExecutorService es = Executors.newSingleThreadExecutor();
    Future<Long> future = es.submit(task);
    try {
      long timeNS = future.get(timeout, TimeUnit.SECONDS);
      debug("\nExecuted in %d second(s)", TimeUnit.NANOSECONDS.toSeconds(timeNS));
      assertThat(map, is(valid()));
    } catch (ExecutionException e) {
      fail("Exception during test: " + e.toString(), e);
    } catch (TimeoutException e) {
      handleTimout(map, es, e);
    } catch (InterruptedException e) {
      fail("", e);
    }
  }

  private void handleTimout(
      ConcurrentLinkedHashMap<?, ?> cache,
      ExecutorService es,
      TimeoutException e) {
    for (StackTraceElement[] trace : Thread.getAllStackTraces().values()) {
      for (StackTraceElement element : trace) {
        info("\tat " + element);
      }
      if (trace.length > 0) {
        info("------");
      }
    }
    es.shutdownNow();
    try {
      es.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      fail("", ex);
    }

    // Print the state of the cache
    debug("Cached Elements: %s", cache.toString());
    debug("Stack Forward:\n%s", lirsToString(cache, Direction.FORWARD, Lirs.STACK));
    debug("Stack Backward:\n%s", lirsToString(cache, Direction.BACKWARD, Lirs.STACK));
    debug("Queue Forward:\n%s", lirsToString(cache, Direction.FORWARD, Lirs.QUEUE));;
    debug("Queue Backward:\n%s", lirsToString(cache, Direction.BACKWARD, Lirs.QUEUE));

    // Print the recorded failures
    for (String failure : failures) {
      debug(failure);
    }
    fail("Spun forever", e);
  }

  enum Lirs { STACK, QUEUE }
  enum Direction { FORWARD, BACKWARD }

  private static String lirsToString(ConcurrentLinkedHashMap<?, ?> map, Direction direction, Lirs collection) {
    map.evictionLock.lock();
    try {
      StringBuilder buffer = new StringBuilder("\n");
      Set<Object> seen = newSetFromMap(new IdentityHashMap<Object, Boolean>());
      ConcurrentLinkedHashMap<?, ?>.Node current = iterate(map.sentinel, direction, collection);
      while (current != map.sentinel) {
        buffer.append(nodeToString(current)).append("\n");
        boolean added = seen.add(current);
        if (!added) {
          buffer.append("Failure: Loop detected\n");
          break;
        }
        current = iterate(current, direction, collection);
      }
      return buffer.toString();
    } finally {
      map.evictionLock.unlock();
    }
  }

  private static ConcurrentLinkedHashMap<?, ?>.Node iterate(
      ConcurrentLinkedHashMap<?, ?>.Node current, Direction direction, Lirs collection) {
    if (collection == Lirs.STACK) {
      return (direction == Direction.FORWARD) ? current.nextInStack : current.prevInStack;
    } else {
      return (direction == Direction.FORWARD) ? current.nextInQueue : current.prevInQueue;
    }
  }

  @SuppressWarnings("unchecked")
  static String nodeToString(Node node) {
    if (node == null) {
      return "null";
    } else if (node.segment == -1) {
      return "setinel";
    }
    return node.key + "=" + node.weightedValue.value;
  }
}
