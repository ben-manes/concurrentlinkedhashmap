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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An single-consumer, multiple-producer bounded buffer backed by an array.
 *
 * @author bmanes@google.com (Ben Manes)
 */
// EXPERIMENTAL: This class will be integrated directly into the map
// TODO(bmanes): Consider skewing the threshold for drain based on the thread
// id, to avoid hitting the #shouldDrain() at the same time and avoiding memory
// barrier (e.g. t1: 16, t2: 20 for same buffer); threshold + thread_id & 0xF
// TODO(bmanes): Make private, only public for benchmark
public final class RingBuffer<E> {
  final AtomicReferenceArray<E> elements;
  final AtomicLong head;
  final AtomicLong tail;
  final long threshold;
  final Sink<E> sink;
  final Lock lock;
  final int mask;

  /**
   * @param estimatedCapacity the actual size is the closest power-of-two
   *   greater than the estimated capacity
   * @param threshold the threshold size before a drain is attempted
   * @param sink the handler to drain elements to
   */
  public RingBuffer(int estimatedCapacity, int threshold, Sink<E> sink) {
    int size = 1;
    while (size < estimatedCapacity) {
      size <<= 1;
    }
    this.sink = sink;
    this.mask = size - 1;
    this.threshold = threshold;
    this.head = new AtomicLong();
    this.tail = new AtomicLong();
    this.lock = new ReentrantLock();
    this.elements = new AtomicReferenceArray<E>(size);
  }

  public int capacity() {
    return elements.length();
  }

  public boolean isEmpty() {
    return head.get() == tail.get();
  }

  /**
   * Inserts the specified element into this queue, waiting if necessary for
   * space to become available and draining if the threshold size was crossed.
   *
   * @param e the element to add
   */
  public void put(E e) {
    // Acquire a slot and spin until accepted. If wrapped then may have multiple
    // producers waiting for the slot & won't be strictly FIFO
    long t = tail.getAndIncrement();
    int index = (int) t & mask;

    for (;;) {
      for (int i = 0; i < 10; i++) {
        if ((elements.get(index) == null) && elements.weakCompareAndSet(index, null, e)) {
          long estimatedSize = (t - head.get());
          if (estimatedSize >= threshold) {
            tryToDrain();
          }
          return;
        }
      }
      tryToDrain();
    }
  }

  // Should never be needed, but demonstrative as simpler than #drain().
  @GuardedBy("lock")
  public E poll() {
    long h = head.get();
    if (h == tail.get()) {
      return null;
    }
    int index = (int) h & mask;
    E e = elements.get(index);
    elements.lazySet(index, null);
    head.lazySet(h + 1);
    return e;
  }

  public void tryToDrain() {
    if (lock.tryLock()) {
      try {
        drain();
      } finally {
        lock.unlock();
      }
    }
  }

  /**
   * Removes all available elements from this buffer and adds them to the
   * {@link Sink}. This operation may be more efficient than repeatedly polling
   * this queue.
   */
  @GuardedBy("lock")
  public void drain() {
    long h = head.get();
    long t = tail.get();
    if (h == t) {
      return;
    }
    do {
      int index = (int) h & mask;
      E e = elements.get(index);
      if (e == null) {
        break;
      }
      elements.lazySet(index, null);
      sink.accept(e);
    } while (h++ != t);
    head.lazySet(h);
  }

  public interface Sink<E> {
    public void accept(E e);
  }
}
