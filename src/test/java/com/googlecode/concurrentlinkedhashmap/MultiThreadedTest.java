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

import java.util.Arrays;
import java.util.Iterator;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;
import org.apache.commons.lang3.SerializationUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentTestHarness.timeTasks;
import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.shuffle;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

/**
 * A unit-test to assert basic concurrency characteristics by validating the
 * internal state after load.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class MultiThreadedTest extends AbstractTest {
  private final Queue<String> failures;
  private final int iterations;
  private final int timeOut;
  private final int threads;

  @Parameters({"iterations", "threads", "timeOut"})
  public MultiThreadedTest(int iterations, int threads, int timeOut) {
    this.failures = new ConcurrentLinkedQueue<String>();
    this.iterations = iterations;
    this.timeOut = timeOut;
    this.threads = threads;
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() {
    failures.clear();
  }

  @Test(dataProvider = "builder")
  public void concurrency(Builder<Integer, Integer> builder) {
    List<Integer> keys = newArrayList();
    Random random = new Random();
    for (int i = 0; i < iterations; i++) {
      keys.add(random.nextInt(iterations / 100));
    }
    final List<List<Integer>> sets = shuffle(threads, keys);
    final ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(capacity())
        .concurrencyLevel(threads)
        .build();
    executeWithTimeOut(map, new Callable<Long>() {
      @Override public Long call() throws Exception {
        return timeTasks(threads, new Thrasher(map, sets));
      }
    });
  }

  @Test(dataProvider = "builder")
  public void concurrency_weighted(Builder<Integer, List<Integer>> builder) {
    final ConcurrentLinkedHashMap<Integer, List<Integer>> map = builder
        .weigher(Weighers.<Integer>list())
        .maximumWeightedCapacity(threads)
        .concurrencyLevel(threads)
        .build();
    final Queue<List<Integer>> values = new ConcurrentLinkedQueue<List<Integer>>();
    for (int i = 1; i <= threads; i++) {
      Integer[] array = new Integer[i];
      Arrays.fill(array, Integer.MIN_VALUE);
      values.add(Arrays.asList(array));
    }
    executeWithTimeOut(map, new Callable<Long>() {
      @Override public Long call() throws Exception {
        return timeTasks(threads, new Runnable() {
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

  /**
   * Executes operations against the map to simulate random load.
   */
  private final class Thrasher implements Runnable {
    private final ConcurrentLinkedHashMap<Integer, Integer> map;
    private final List<List<Integer>> sets;
    private final AtomicInteger index;

    public Thrasher(ConcurrentLinkedHashMap<Integer, Integer> map, List<List<Integer>> sets) {
      this.index = new AtomicInteger();
      this.map = map;
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
          operation.execute(map, key);
        } catch (RuntimeException e) {
          String error =
              String.format("Failed: key %s on operation %s for node %s",
                  key, operation, nodeToString(findNode(key, map)));
          failures.add(error);
          throw e;
        } catch (Throwable thr) {
          String error =
              String.format("Halted: key %s on operation %s for node %s",
                  key, operation, nodeToString(findNode(key, map)));
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
        cache.setCapacity((int) cache.capacity());
      }
    },
    GET() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.get(key);
      }
    },
    GET_QUIETLY() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        cache.getQuietly(key);
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
   ASCENDING() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Integer i : cache.ascendingKeySet()) {
          checkNotNull(i);
        }
        for (Entry<Integer, Integer> entry : cache.ascendingMap().entrySet()) {
          checkNotNull(entry);
        }
      }
    },
    DESCENDING() {
      @Override void execute(ConcurrentLinkedHashMap<Integer, Integer> cache, Integer key) {
        for (Integer i : cache.descendingKeySet()) {
          checkNotNull(i);
        }
        for (Entry<Integer, Integer> entry : cache.descendingMap().entrySet()) {
          checkNotNull(entry);
        }
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
      long timeNS = future.get(timeOut, SECONDS);
      debug("\nExecuted in %d second(s)", NANOSECONDS.toSeconds(timeNS));
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
      es.awaitTermination(10, SECONDS);
    } catch (InterruptedException ex) {
      fail("", ex);
    }

    // Print the state of the cache
    debug("Cached Elements: %s", cache.toString());
    debug("Deque Forward:\n%s", ascendingToString(cache));
    debug("Deque Backward:\n%s", descendingToString(cache));

    // Print the recorded failures
    for (String failure : failures) {
      debug(failure);
    }
    fail("Spun forever", e);
  }

  static String ascendingToString(ConcurrentLinkedHashMap<?, ?> map) {
    return policyToString(map, true);
  }

  static String descendingToString(ConcurrentLinkedHashMap<?, ?> map) {
    return policyToString(map, false);
  }

  @SuppressWarnings("rawtypes")
  private static String policyToString(ConcurrentLinkedHashMap<?, ?> map, boolean ascending) {
    map.evictionLock.lock();
    try {
      StringBuilder buffer = new StringBuilder("\n");
      Set<Object> seen = Sets.newIdentityHashSet();
      Iterator<? extends Node> iterator = ascending
          ? map.policy.ascendingIterator()
          : map.policy.descendingIterator();
      while (iterator.hasNext()) {
        Node node = iterator.next();
        buffer.append(nodeToString(node)).append("\n");
        boolean added = seen.add(node);
        if (!added) {
          buffer.append("Failure: Loop detected\n");
          break;
        }
      }
      return buffer.toString();
    } finally {
      map.evictionLock.unlock();
    }
  }

  @SuppressWarnings("rawtypes")
  static String nodeToString(Node node) {
    return (node == null) ? "null" : String.format("%s=%s", node.key, node.getValue());
  }

  /** Finds the node in the map by walking the list. Returns null if not found. */
  static Node<Integer, Integer> findNode(final Object key,
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.evictionLock.lock();
    try {
      Predicate<Node<Integer, Integer>> forKey = new Predicate<Node<Integer, Integer>>() {
        @Override public boolean apply(Node<Integer, Integer> node) {
          return node.key.equals(key);
        }
      };
      return Iterators.tryFind(map.policy.ascendingIterator(), forKey).orNull();
    } finally {
      map.evictionLock.unlock();
    }
  }
}
