/*
 * Copyright 2011 Benjamin Manes
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

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_CAPACITY;
import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_WEIGHT;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A unit-test for the weigher algorithms and that the map keeps track of the
 * weighted sizes.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class WeigherTest extends BaseTest {

  @Override
  protected int capacity() {
    return 100;
  }

  @Test(expectedExceptions = AssertionError.class)
  public void constructor() throws Throwable {
    try {
      Constructor<?> constructors[] = Weighers.class.getDeclaredConstructors();
      assertThat(constructors.length, is(1));
      constructors[0].setAccessible(true);
      constructors[0].newInstance((Object[]) null);
      fail("Expected a failure to instantiate");
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  @Test(dataProvider = "singletonWeigher")
  public void singleton(Weigher<Object> weigher) {
    assertThat(weigher.weightOf(new Object()), is(1));
    assertThat(weigher.weightOf(emptyList()), is(1));
    assertThat(weigher.weightOf(asList(1, 2, 3)), is(1));
  }

  @Test(dataProvider = "byteArrayWeigher")
  public void byteArray(Weigher<byte[]> weigher) {
    assertThat(weigher.weightOf(new byte[]{}), is(0));
    assertThat(weigher.weightOf(new byte[] {1}), is(1));
    assertThat(weigher.weightOf(new byte[] {1, 2, 3}), is(3));
  }

  @Test(dataProvider = "iterableWeigher")
  public void iterable(Weigher<Iterable<?>> weigher) {
    assertThat(weigher.weightOf(emptyList()), is(0));
    assertThat(weigher.weightOf(asIterable(emptyList())), is(0));
    assertThat(weigher.weightOf(asList(1)), is(1));
    assertThat(weigher.weightOf(asList(1, 2, 3)), is(3));
    assertThat(weigher.weightOf(asIterable(asList(1, 2, 3))), is(3));
  }

  @Test(dataProvider = "collectionWeigher")
  public void collection(Weigher<Collection<?>> weigher) {
    assertThat(weigher.weightOf(emptyList()), is(0));
    assertThat(weigher.weightOf(asList(1)), is(1));
    assertThat(weigher.weightOf(asList(1, 2, 3)), is(3));
  }

  @Test(dataProvider = "listWeigher")
  public void list(Weigher<List<?>> weigher) {
    assertThat(weigher.weightOf(emptyList()), is(0));
    assertThat(weigher.weightOf(asList(1)), is(1));
    assertThat(weigher.weightOf(asList(1, 2, 3)), is(3));
  }

  @Test(dataProvider = "setWeigher")
  public void set(Weigher<Set<?>> weigher) {
    assertThat(weigher.weightOf(emptySet()), is(0));
    assertThat(weigher.weightOf(ImmutableSet.of(1)), is(1));
    assertThat(weigher.weightOf(ImmutableSet.of(1, 2, 3)), is(3));
  }

  @Test(dataProvider = "mapWeigher")
  public void map(Weigher<Map<?, ?>> weigher) {
    assertThat(weigher.weightOf(emptyMap()), is(0));
    assertThat(weigher.weightOf(singletonMap(1, 2)), is(1));
    assertThat(weigher.weightOf(ImmutableMap.of(1, 2, 2, 3, 3, 4)), is(3));
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void put_withNegativeWeight(Builder<Integer, Integer> builder) {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return -1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void put_withZeroWeight(Builder<Integer, Integer> builder) {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return 0;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(dataProvider = "builder", expectedExceptions = IllegalArgumentException.class)
  public void put_withAboveMaximumWeight(Builder<Integer, Integer> builder) {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return MAXIMUM_WEIGHT + 1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(dataProvider = "builder")
  public void put_withIntegerOverflow(Builder<Integer, Integer> builder) {
    final boolean[] useMax = { true };
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(MAXIMUM_CAPACITY)
        .weigher(new Weigher<Integer>() {
          @Override public int weightOf(Integer value) {
            return useMax[0] ? MAXIMUM_WEIGHT : 1;
          }
        }).build();
    map.putAll(ImmutableMap.of(1, 1, 2, 2));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(MAXIMUM_CAPACITY));
    useMax[0] = false;
    map.put(3, 3);
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(MAXIMUM_WEIGHT + 1));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void put(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.put("a", asList(1, 2, 3));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(3));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void put_sameWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.put("a", asList(-1, -2, -3));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void put_changeWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.put("a", asList(-1, -2, -3, -4));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(5));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void replace_sameWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.replace("a", asList(-1, -2, -3));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void replace_changeWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.replace("a", asList(-1, -2, -3, -4));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(5));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void replaceConditionally_sameWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    assertThat(map.replace("a", asList(1, 2, 3), asList(4, 5, 6)), is(true));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void replaceConditionally_changeWeight(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.replace("a", asList(1, 2, 3), asList(-1, -2, -3, -4));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(5));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void replaceConditionally_fails(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    assertThat(map.replace("a", asList(1), asList(4, 5)), is(false));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void remove(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    assertThat(map.remove("a"), is(asList(1, 2, 3)));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(1));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void removeConditionally(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    assertThat(map.remove("a", asList(1, 2, 3)), is(true));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(1));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void removeConditionally_fails(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    assertThat(map.remove("a", asList(-1, -2, -3)), is(false));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));
  }

  @Test(dataProvider = "guardedWeightedMap")
  public void clear(ConcurrentLinkedHashMap<String, List<Integer>> map) {
    map.putAll(ImmutableMap.of("a", asList(1, 2, 3), "b", asList(1)));

    map.clear();
    assertThat(map.size(), is(0));
    assertThat(map.weightedSize(), is(0));
  }

  private <E> Iterable<E> asIterable(final Collection<E> c) {
    return new Iterable<E>() {
      @Override public Iterator<E> iterator() {
        return c.iterator();
      }
    };
  }
}
