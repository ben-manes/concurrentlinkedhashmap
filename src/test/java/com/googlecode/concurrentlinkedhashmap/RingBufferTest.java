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
import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import com.googlecode.concurrentlinkedhashmap.RingBuffer.Sink;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A unit-test for the ring buffer algorithm.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "experimental")
public class RingBufferTest extends AbstractTest {
  @Captor ArgumentCaptor<Integer> element;
  @Mock Sink<Integer> sink;
  final int iterations;
  final int threshold;
  final int threads;
  final int timeOut;

  @Parameters({"iterations", "threshold", "threads", "timeOut"})
  public RingBufferTest(int iterations, int threshold, int threads, int timeOut) {
    this.iterations = iterations;
    this.threshold = threshold;
    this.threads = threads;
    this.timeOut = timeOut;
  }

  @Test(dataProvider = "buffer")
  public void put_whenEmpty(RingBuffer<Integer> buffer) {
    for (int i = 0; i < buffer.length(); i++) {
      buffer.put(i);
    }
    for (int i = 0; i < buffer.length(); i++) {
      assertThat(buffer.get(i), either(is(i)).or(is(nullValue())));
    }
    assertThat(buffer.tail.get(), is((long) buffer.length()));
  }

  @Test(dataProvider = "buffer")
  public void put_whenFull(final RingBuffer<Integer> buffer) throws Exception {
    final AtomicBoolean done = new AtomicBoolean();
    buffer.lock.lock();
    new Thread() {
      @Override public void run() {
        for (int i = 0; i < buffer.length() + 1; i++) {
          buffer.put(i);
        }
        done.set(true);
      }
    }.start();
    await().until(new Callable<Integer>() {
      @Override public Integer call() {
        return (int) buffer.tail.get();
      }
    }, is(buffer.length() + 1));
    assertThat(done.get(), is(false));
    buffer.drain();
    buffer.lock.unlock();
    await().until(new Callable<Boolean>() {
      @Override public Boolean call() {
        return done.get();
      }
    });
    int expected = buffer.length() + (buffer.isEmpty() ? 1 : 0);
    verify(sink, times(expected)).accept(element.capture());
  }

  @Test(dataProvider = "buffer")
  public void drain_whenEmpty(RingBuffer<Integer> buffer) {
    buffer.drain();
    verify(sink, never()).accept(anyInt());
  }

  @Test(dataProvider = "buffer")
  public void drain_whenFull(RingBuffer<Integer> buffer) {
    for (int i = 0; i < buffer.length(); i++) {
      buffer.put(i);
    }
    buffer.drain();
    verify(sink, times(buffer.length())).accept(element.capture());
    for (int i = 0; i < buffer.length(); i++) {
      assertThat(element.getAllValues().get(i), is(i));
    }
  }

  @Test(dataProvider = "buffer")
  public void concurrency(final RingBuffer<Integer> buffer) {
    final Lock lock = new ReentrantLock();
    final Runnable task = new Runnable() {
      @Override public void run() {
        for (int i = 0; i < iterations; i++) {
          buffer.put(i);
        }
      }
    };
    ExecutorService es = Executors.newSingleThreadExecutor();
    Future<Long> future = es.submit(new Callable<Long>() {
      @Override public Long call() throws InterruptedException {
        return timeTasks(threads, task);
      }
    });
    try {
      long timeNS = future.get(timeOut, SECONDS);
      debug("\nExecuted in %d second(s)", NANOSECONDS.toSeconds(timeNS));
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
      fail();
    } catch (InterruptedException e) {
      fail("", e);
    } finally {
      es.shutdownNow();
    }
  }

  @DataProvider(name = "buffer")
  public Object[][] providesRingBuffer() {
    return new Object[][] {{ new RingBuffer<Integer>(capacity(), threshold, sink) }};
  }
}
