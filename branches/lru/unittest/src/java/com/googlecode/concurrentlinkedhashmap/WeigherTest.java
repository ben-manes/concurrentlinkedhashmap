package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.MAXIMUM_WEIGHT;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

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

  @Test
  @SuppressWarnings("unchecked")
  public void singleton() {
    assertEquals(Weighers.singleton().weightOf(new Object()), 1);
    assertEquals(Weighers.singleton().weightOf(Arrays.asList()), 1);
    assertEquals(Weighers.singleton().weightOf(Arrays.asList(1, 2, 3)), 1);
  }

  @Test
  public void byteArray() {
    assertEquals(Weighers.byteArray().weightOf(new byte[]{}), 0);
    assertEquals(Weighers.byteArray().weightOf(new byte[] {1}), 1);
    assertEquals(Weighers.byteArray().weightOf(new byte[] {1, 2, 3}), 3);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void iterable() {
    assertEquals(Weighers.iterable().weightOf(Arrays.asList()), 0);
    assertEquals(Weighers.iterable().weightOf(asIterable(Arrays.asList())), 0);
    assertEquals(Weighers.<Integer>iterable().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>iterable().weightOf(Arrays.asList(1, 2, 3)), 3);
    assertEquals(Weighers.<Integer>iterable().weightOf(asIterable(Arrays.asList(1, 2, 3))), 3);
  }

  @Test
  public void collection() {
    assertEquals(Weighers.collection().weightOf(Collections.emptyList()), 0);
    assertEquals(Weighers.<Integer>collection().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>collection().weightOf(Arrays.asList(1, 2, 3)), 3);
  }

  @Test
  public void list() {
    assertEquals(Weighers.list().weightOf(Collections.emptyList()), 0);
    assertEquals(Weighers.<Integer>list().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>list().weightOf(Arrays.asList(1, 2, 3)), 3);
  }

  @Test
  public void set() {
    assertEquals(Weighers.set().weightOf(Collections.emptySet()), 0);
    assertEquals(Weighers.<Integer>set().weightOf(Collections.singleton(1)), 1);
    assertEquals(
        Weighers.<Integer>set().weightOf(new HashSet<Integer>(Arrays.asList(1, 2, 3))), 3);
  }

  @Test
  public void map() {
    assertEquals(Weighers.map().weightOf(Collections.emptyMap()), 0);
    assertEquals(Weighers.<Integer, Integer>map().weightOf(Collections.singletonMap(1, 2)), 1);

    Map<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(1, 2);
    map.put(2, 3);
    map.put(3, 4);
    assertEquals(Weighers.<Integer, Integer>map().weightOf(map), 3);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void weightedValue_withNegative() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override
      public int weightOf(Integer value) {
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
      @Override
      public int weightOf(Integer value) {
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
      @Override
      public int weightOf(Integer value) {
        return MAXIMUM_WEIGHT + 1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .weigher(weigher).build();
    map.put(1, 2);
  }

  @Test
  public void weightedValue_withCollections() {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
        .weigher(Weighers.<Integer>collection())
        .maximumWeightedCapacity(capacity())
        .build();

    // add first
    map.put(1, Arrays.asList(1, 2, 3));
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 3);

    // add second
    map.put(2, Arrays.asList(1));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 4);

    // put as update
    map.put(1, Arrays.asList(-4, -5, -6, -7));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 5);

    // put as update with same weight
    map.put(1, Arrays.asList(4, 5, 6, 7));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 5);

    // replace
    map.replace(2, Arrays.asList(-8, -9, -10));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // replace with same weight
    map.replace(2, Arrays.asList(8, 9, 10));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // fail to replace conditionally
    assertFalse(map.replace(2, Arrays.asList(-1), Arrays.asList(11, 12)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 7);

    // replace conditionally
    assertTrue(map.replace(2, Arrays.asList(8, 9, 10), Arrays.asList(11, 12)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 6);

    // replace conditionally with same weight
    assertTrue(map.replace(2, Arrays.asList(11, 12), Arrays.asList(13, 14)));
    assertEquals(map.size(), 2);
    assertEquals(map.weightedSize(), 6);

    // remove
    map.remove(2);
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 4);

    // fail to remove conditionally
    map.remove(1, Arrays.asList(-1));
    assertEquals(map.size(), 1);
    assertEquals(map.weightedSize(), 4);

    // remove conditionally
    map.remove(1, Arrays.asList(4, 5, 6, 7));
    assertEquals(map.size(), 0);
    assertEquals(map.weightedSize(), 0);

    // clear
    map.put(3, Arrays.asList(1, 2, 3));
    map.put(4, Arrays.asList(4, 5, 6));
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
    map.put(1, 1);
    map.put(2, 2);
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
