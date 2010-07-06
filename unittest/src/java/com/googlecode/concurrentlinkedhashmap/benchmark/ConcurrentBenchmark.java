package com.googlecode.concurrentlinkedhashmap.benchmark;

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
