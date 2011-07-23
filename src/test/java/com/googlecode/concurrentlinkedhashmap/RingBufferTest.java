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

import static com.jayway.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.googlecode.concurrentlinkedhashmap.RingBuffer.Sink;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A unit-test for the ring buffer algorithm.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public class RingBufferTest extends AbstractTest {
  @Captor ArgumentCaptor<Integer> element;
  @Mock Sink<Integer> sink;

  @Test(dataProvider = "emptyBuffer")
  public void put_whenEmpty(RingBuffer<Integer> buffer) {
    warmUp(buffer);
    for (int i = 0; i < buffer.capacity(); i++) {
      assertThat(buffer.elements.get(i), is(i));
    }
    assertThat(buffer.tail.get(), is((long) buffer.capacity()));
  }

  @Test(dataProvider = "warmedBuffer")
  public void put_whenFull(final RingBuffer<Integer> buffer) throws Exception {
    final AtomicBoolean done = new AtomicBoolean();
    new Thread() {
      @Override public void run() {
        buffer.put(0);
        done.set(true);
      }
    }.start();
    await().until(new Callable<Integer>() {
      @Override public Integer call() {
        return (int) buffer.tail.get();
      }
    }, is(buffer.capacity() + 1));
    assertThat(done.get(), is(false));
    buffer.drain();
    await().until(new Callable<Boolean>() {
      @Override public Boolean call() {
        return done.get();
      }
    });
    int expected = buffer.capacity() + (buffer.isEmpty() ? 1 : 0);
    verify(sink, times(expected)).accept(element.capture());
  }

  @Test(dataProvider = "emptyBuffer")
  public void drain_whenEmpty(RingBuffer<Integer> buffer) {
    buffer.drain();
    verify(sink, never()).accept(anyInt());
  }

  @Test(dataProvider = "warmedBuffer")
  public void drain_whenFull(RingBuffer<Integer> buffer) {
    buffer.drain();
    verify(sink, times(buffer.capacity())).accept(element.capture());
    for (int i = 0; i < buffer.capacity(); i++) {
      assertThat(element.getAllValues().get(i), is(i));
    }
  }

  @DataProvider(name = "emptyBuffer")
  public Object[][] providesEmptyRingBuffer() {
    return new Object[][] {{ new RingBuffer<Integer>(capacity(), sink) }};
  }

  @DataProvider(name = "warmedBuffer")
  public Object[][] providesWarmedRingBuffer() {
    RingBuffer<Integer> buffer = new RingBuffer<Integer>(capacity(), sink);
    warmUp(buffer);
    return new Object[][] {{ buffer }};
  }

  protected static void warmUp(RingBuffer<Integer> buffer) {
    for (int i = 0; i < buffer.capacity(); i++) {
      assertThat(buffer.put(i), is(greaterThan(0)));
    }
  }
}
