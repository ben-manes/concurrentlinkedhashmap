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
package com.googlecode.concurrentlinkedhashmap.benchmark;

import com.google.caliper.Param;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;

import com.googlecode.concurrentlinkedhashmap.RingBuffer;
import com.googlecode.concurrentlinkedhashmap.RingBuffer.Sink;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This benchmark evaluates single-threaded performance of the buffer
 * approaches.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class RingBufferBenchmark extends SimpleBenchmark {
  @Param("16")
  int threshold;

  RingBuffer<Integer> buffer;
  Sink<Integer> sink;

  Queue<Integer> queue;
  AtomicInteger length;

  @Override
  protected void setUp() {
    sink = new Sink<Integer>() { @Override public void accept(Integer i) {} };
    buffer = new RingBuffer<Integer>(4 * threshold, sink);
    queue = new ConcurrentLinkedQueue<Integer>();
    length = new AtomicInteger();
  }

  public int timeRingBuffer(final int reps) {
    int dummy = 0;
    for (int i = 0; i < reps; i++) {
      if (buffer.put(i) > threshold) {
        buffer.drain();
        dummy++;
      }
    }
    return dummy;
  }

  public int timeConcurrentLinkedQueue(final int reps) {
    Integer dummy = 0;
    for (int i = 0; i < reps; i++) {
      queue.add(i);
      if (length.incrementAndGet() > threshold) {
        int removedFromBuffer = 0;
        while ((dummy = queue.poll()) != null) {
          sink.accept(dummy);
          removedFromBuffer++;
        }
        dummy = removedFromBuffer;
        length.addAndGet(-removedFromBuffer);
      }
    }
    return dummy;
  }

  @Test(groups = "caliper")
  @Parameters({"warmupMillis", "runMillis", "timeUnit"})
  public static void benchmark(String warmupMillis, String runMillis, String timeUnit) {
    String[] args = {
      "--warmupMillis", warmupMillis,
      "--runMillis", runMillis,
      "--timeUnit", timeUnit
    };
    Runner.main(RingBufferBenchmark.class, args);
  }
}
