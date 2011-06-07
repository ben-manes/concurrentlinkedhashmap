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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.caliper.SimpleBenchmark;

import java.util.concurrent.CountDownLatch;

/**
 * A benchmark that provides scaffolding for multi-threaded testing.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public abstract class ConcurrentBenchmark extends SimpleBenchmark {
  private CountDownLatch startGate, endGate;
  private volatile Runnable task;

  @Override
  protected final void setUp() throws Exception {
    checkArgument(getNumberOfThreads() > 0);

    startGate = new CountDownLatch(1);
    endGate = new CountDownLatch(getNumberOfThreads());
    for (int i = 0; i < getNumberOfThreads(); i++) {
      Thread thread = new Thread() {
        @Override public void run() {
          try {
            startGate.await();
            try {
              task.run();
            } finally {
              endGate.countDown();
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      };
      thread.setDaemon(true);
      thread.start();
    }
    benchmarkSetUp();
  }

  @Override
  protected final void tearDown() {}

  /** The benchmark's setup handling */
  protected void benchmarkSetUp() throws Exception {}

  /** The benchmark's tear down handling */
  protected void benchmarkTearDown() throws Exception {}

  protected final void concurrent(Runnable runner) {
    task = runner;
    startGate.countDown();
    try {
      endGate.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /** The number of threads to run concurrently */
  protected abstract int getNumberOfThreads();
}
