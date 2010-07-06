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
import static org.hamcrest.Matchers.notNullValue;
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

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void weightedValue_withNegative() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return -1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void weightedValue_withZero() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return 0;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void weightedValue_withAboveMaximum() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override public int weightOf(Integer value) {
        return MAXIMUM_WEIGHT + 1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test(dataProvider = "collectionWeigher")
  public void weightedValue_withCollections(Weigher<Collection<Integer>> weigher) {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
        .maximumWeightedCapacity(capacity())
        .weigher(weigher)
        .build();

    // add first
    map.put(1, asList(1, 2, 3));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(3));

    // add second
    map.put(2, asList(1));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(4));

    // put as update
    map.put(1, asList(-4, -5, -6, -7));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(5));

    // put as update with same weight
    map.put(1, asList(4, 5, 6, 7));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(5));

    // replace
    map.replace(2, asList(-8, -9, -10));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(7));

    // replace with same weight
    map.replace(2, asList(8, 9, 10));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(7));

    // fail to replace conditionally
    assertThat(map.replace(2, asList(-1), asList(11, 12)), is(false));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(7));

    // replace conditionally
    assertThat(map.replace(2, asList(8, 9, 10), asList(11, 12)), is(true));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(6));

    // replace conditionally with same weight
    assertThat(map.replace(2, asList(11, 12), asList(13, 14)), is(true));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(6));

    // remove
    assertThat(map.remove(2), is(notNullValue()));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(4));

    // fail to remove conditionally
    assertThat(map.remove(1, asList(-1)), is(false));
    assertThat(map.size(), is(1));
    assertThat(map.weightedSize(), is(4));

    // remove conditionally
    assertThat(map.remove(1, asList(4, 5, 6, 7)), is(true));
    assertThat(map.size(), is(0));
    assertThat(map.weightedSize(), is(0));

    // clear
    map.put(3, asList(1, 2, 3));
    map.put(4, asList(4, 5, 6));
    map.clear();
    assertThat(map.size(), is(0));
    assertThat(map.weightedSize(), is(0));
  }

  @Test
  public void integerOverflow() {
    final boolean[] useMax = {true};
    Builder<Integer, Integer> builder = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(new Weigher<Integer>() {
          @Override public int weightOf(Integer value) {
            return useMax[0] ? MAXIMUM_WEIGHT : 1;
          }
        });
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(MAXIMUM_CAPACITY)
        .build();
    map.putAll(ImmutableMap.of(1, 1, 2, 2));
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(MAXIMUM_CAPACITY));
    useMax[0] = false;
    map.put(3, 3);
    assertThat(map.size(), is(2));
    assertThat(map.weightedSize(), is(MAXIMUM_WEIGHT + 1));
  }

  private <E> Iterable<E> asIterable(final Collection<E> c) {
    return new Iterable<E>() {
      @Override public Iterator<E> iterator() {
        return c.iterator();
      }
    };
  }
}
