package com.rc.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A testing harness for concurrency related executions.
 *
 * This harness will ensure that all threads execute at the same instance, records
 * the full execution time, and optionally retrieves the responses from each thread.
 * This harness can be used for performance tests, investigations of lock contention,
 * etc.
 *
 * This code was adapted from <tt>Java Concurrency in Practice</tt>, using an example
 * of a {@link CountDownLatch} for starting and stopping threads in timing tests.
 *
 * @author  <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ConcurrentTestHarness {

    private ConcurrentTestHarness() {
        throw new IllegalStateException("Cannot instantiate static class");
    }

    /**
     * Executes a task, on N threads, all starting at the exact same instant.
     *
     * @param nThreads The number of threads to execute.
     * @param task     The task to execute in each thread.
     * @return         The execution time for all threads to complete, in nanoseconds.
     */
    public static long timeTasks(int nThreads, Runnable task) throws InterruptedException {
        return timeTasks(nThreads, task, "Thread");
    }

    /**
     * Executes a task, on N threads, all starting at the exact same instant.
     *
     * @param nThreads       The number of threads to execute.
     * @param task           The task to execute in each thread.
     * @param baseThreadName The base name for each thread in this task set.
     * @return               The execution time for all threads to complete, in nanoseconds.
     */
    public static long timeTasks(int nThreads, Runnable task, String baseThreadName) throws InterruptedException {
        return timeTasks(nThreads, Executors.callable(task), baseThreadName).getExecutionTime();
    }

    /**
     * Executes a task, on N threads, all starting at the exact same instant.
     *
     * @param nThreads The number of threads to execute.
     * @param task     The task to execute in each thread.
     * @return         The result of each task and the full execution time, in nanoseconds.
     */
    public static <T> TestResult<T> timeTasks(int nThreads, Callable<T> task) throws InterruptedException {
        return timeTasks(nThreads, task, "Thread");
    }

    /**
     * Executes a task, on N threads, all starting at the exact same instant.
     *
     * @param nThreads       The number of threads to execute.
     * @param task           The task to execute in each thread.
     * @param baseThreadName The base name for each thread in this task set.
     * @return               The result of each task and the full execution time, in nanoseconds.
     */
    public static <T> TestResult<T> timeTasks(int nThreads, final Callable<T> task, final String baseThreadName) throws InterruptedException {
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(nThreads);
        final AtomicReferenceArray<T> results = new AtomicReferenceArray<T>(nThreads);

        for (int i = 0; i < nThreads; i++) {
            final int index = i;
            new Thread(baseThreadName + "-" + i) {
                @Override
                public void run() {
                    try {
                        startGate.await();
                        try {
                            results.set(index, task.call());
                        } finally {
                            endGate.countDown();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }.start();
        }

        long start = System.nanoTime();
        startGate.countDown();
        endGate.await();
        long end = System.nanoTime();

        return new TestResult<T>(end - start, toList(results));
    }

    /**
     * Migrates the data from the atomic array to a {@link List} for easier consumption.
     *
     * @param data The per-thread results from the test.
     * @return     The per-thread results as a standard collection.
     */
    private static <T> List<T> toList(AtomicReferenceArray<T> data) {
        List<T> list = new ArrayList<T>(data.length());
        for (int i=0; i<data.length(); i++) {
            list.add(data.get(i));
        }
        return list;
    }

    /**
     * The results of the test harness's execution.
     *
     * @param <T> The data type produced by the task.
     */
    public static final class TestResult<T> {
        private final long executionTime;
        private final List<T> results;

        public TestResult(long executionTime, List<T> results) {
            this.executionTime = executionTime;
            this.results = results;
        }

        /**
         * The test's execution time, in nanoseconds.
         *
         * @return The time to complete the test.
         */
        public long getExecutionTime() {
            return executionTime;
        }

        /**
         * The results from executing the tasks.
         *
         * @return The outputs from the tasks.
         */
        public List<T> getResults() {
            return results;
        }
    }
}
