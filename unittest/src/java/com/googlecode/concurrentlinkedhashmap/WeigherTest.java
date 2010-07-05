package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_WEIGHT;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;

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
      assertEquals(constructors.length, 1);
      constructors[0].setAccessible(true);
      constructors[0].newInstance((Object[]) null);
      fail("Expected a failure to instantiate");
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  @Test(dataProvider = "singletonWeigher")
  public void singleton(Weigher<Object> weigher) {
    assertEquals(weigher.weightOf(new Object()), 1);
    assertEquals(weigher.weightOf(emptyList()), 1);
    assertEquals(weigher.weightOf(asList(1, 2, 3)), 1);
  }

  @Test(dataProvider = "byteArrayWeigher")
  public void byteArray(Weigher<byte[]> weigher) {
    assertEquals(weigher.weightOf(new byte[]{}), 0);
    assertEquals(weigher.weightOf(new byte[] {1}), 1);
    assertEquals(weigher.weightOf(new byte[] {1, 2, 3}), 3);
  }

  @Test(dataProvider = "iterableWeigher")
  public void iterable(Weigher<Iterable<?>> weigher) {
    assertEquals(weigher.weightOf(emptyList()), 0);
    assertEquals(weigher.weightOf(asIterable(emptyList())), 0);
    assertEquals(weigher.weightOf(asList(1)), 1);
    assertEquals(weigher.weightOf(asList(1, 2, 3)), 3);
    assertEquals(weigher.weightOf(asIterable(asList(1, 2, 3))), 3);
  }

  @Test(dataProvider = "collectionWeigher")
  public void collection(Weigher<Collection<?>> weigher) {
    assertEquals(weigher.weightOf(emptyList()), 0);
    assertEquals(weigher.weightOf(asList(1)), 1);
    assertEquals(weigher.weightOf(asList(1, 2, 3)), 3);
  }

  @Test(dataProvider = "listWeigher")
  public void list(Weigher<List<?>> weigher) {
    assertEquals(weigher.weightOf(emptyList()), 0);
    assertEquals(weigher.weightOf(asList(1)), 1);
    assertEquals(weigher.weightOf(asList(1, 2, 3)), 3);
  }

  @Test(dataProvider = "setWeigher")
  public void set(Weigher<Set<?>> weigher) {
    assertEquals(weigher.weightOf(emptySet()), 0);
    assertEquals(weigher.weightOf(ImmutableSet.of(1)), 1);
    assertEquals(weigher.weightOf(ImmutableSet.of(1, 2, 3)), 3);
  }

  @Test(dataProvider = "mapWeigher")
  public void map(Weigher<Map<?, ?>> weigher) {
    assertEquals(weigher.weightOf(emptyMap()), 0);
    assertEquals(weigher.weightOf(singletonMap(1, 2)), 1);
    assertEquals(weigher.weightOf(ImmutableMap.of(1, 2, 2, 3, 3, 4)), 3);
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
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 3);

    // add second
    map.put(2, asList(1));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 4);

    // put as update
    map.put(1, asList(-4, -5, -6, -7));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 5);

    // put as update with same weight
    map.put(1, asList(4, 5, 6, 7));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 5);

    // replace
    map.replace(2, asList(-8, -9, -10));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // replace with same weight
    map.replace(2, asList(8, 9, 10));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // fail to replace conditionally
    assertFalse(map.replace(2, asList(-1), asList(11, 12)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // replace conditionally
    assertTrue(map.replace(2, asList(8, 9, 10), asList(11, 12)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 6);

    // replace conditionally with same weight
    assertTrue(map.replace(2, asList(11, 12), asList(13, 14)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 6);

    // remove
    map.remove(2);
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 4);

    // fail to remove conditionally
    map.remove(1, asList(-1));
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 4);

    // remove conditionally
    map.remove(1, asList(4, 5, 6, 7));
    assertEquals(map.size(), 0);
    assertEquals(map.weightedSize(), 0);

    // clear
    map.put(3, asList(1, 2, 3));
    map.put(4, asList(4, 5, 6));
    map.clear();
    assertEquals(map.size(), 0);
    assertEquals(map.weightedSize(), 0);
  }

  @Test
  public void integerOverflow() {
    final boolean[] useMax = {true};
    Builder<Integer, Integer> builder = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(new Weigher<Integer>() {
          @Override public int weightOf(Integer value) {
            return useMax[0] ? ConcurrentLinkedHashMap.MAXIMUM_WEIGHT : 1;
          }
        });
    ConcurrentLinkedHashMap<Integer, Integer> map = builder
        .maximumWeightedCapacity(ConcurrentLinkedHashMap.MAXIMUM_CAPACITY)
        .build();
    map.putAll(ImmutableMap.of(1, 1, 2, 2));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), ConcurrentLinkedHashMap.MAXIMUM_CAPACITY);
    useMax[0] = false;
    map.put(3, 3);
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), ConcurrentLinkedHashMap.MAXIMUM_WEIGHT + 1);
  }

  private <E> Iterable<E> asIterable(final Collection<E> c) {
    return new Iterable<E>() {
      @Override public Iterator<E> iterator() {
        return c.iterator();
      }
    };
  }
}
