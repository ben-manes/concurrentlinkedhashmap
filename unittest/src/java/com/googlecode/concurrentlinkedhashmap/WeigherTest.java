package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A unit-test for the weigher algorithms and that the map keeps track of the
 * weighted sizes.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class WeigherTest extends BaseTest {

  @Test(groups = "development")
  public void singleton() {
    assertEquals(Weighers.singleton().weightOf(new Object()), 1);
    assertEquals(Weighers.singleton().weightOf(Arrays.asList()), 1);
    assertEquals(Weighers.singleton().weightOf(Arrays.asList(1, 2, 3)), 1);
  }

  @Test(groups = "development")
  public void byteArray() {
    assertEquals(Weighers.byteArray().weightOf(new byte[]{}), 0);
    assertEquals(Weighers.byteArray().weightOf(new byte[] {1}), 1);
    assertEquals(Weighers.byteArray().weightOf(new byte[] {1, 2, 3}), 3);
  }

  @Test(groups = "development")
  public void iterable() {
    assertEquals(Weighers.iterable().weightOf(Arrays.asList()), 0);
    assertEquals(Weighers.iterable().weightOf(asIterable(Arrays.asList())), 0);
    assertEquals(Weighers.<Integer>iterable().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>iterable().weightOf(Arrays.asList(1, 2, 3)), 3);
    assertEquals(Weighers.<Integer>iterable().weightOf(asIterable(Arrays.asList(1, 2, 3))), 3);
  }

  @Test(groups = "development")
  public void collection() {
    assertEquals(Weighers.collection().weightOf(Collections.emptyList()), 0);
    assertEquals(Weighers.<Integer>collection().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>collection().weightOf(Arrays.asList(1, 2, 3)), 3);
  }

  @Test(groups = "development")
  public void list() {
    assertEquals(Weighers.list().weightOf(Collections.emptyList()), 0);
    assertEquals(Weighers.<Integer>list().weightOf(Arrays.asList(1)), 1);
    assertEquals(Weighers.<Integer>list().weightOf(Arrays.asList(1, 2, 3)), 3);
  }

  @Test(groups = "development")
  public void set() {
    assertEquals(Weighers.set().weightOf(Collections.emptySet()), 0);
    assertEquals(Weighers.<Integer>set().weightOf(Collections.singleton(1)), 1);
    assertEquals(Weighers.<Integer>set().weightOf(new HashSet<Integer>(Arrays.asList(1, 2, 3))), 3);
  }

  @Test(groups = "development")
  public void map() {
    assertEquals(Weighers.map().weightOf(Collections.emptyMap()), 0);
    assertEquals(Weighers.<Integer, Integer>map().weightOf(Collections.singletonMap(1, 2)), 1);

    Map<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(1, 2);
    map.put(2, 3);
    map.put(3, 4);
    assertEquals(Weighers.<Integer, Integer>map().weightOf(map), 3);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void weightedValue_withNegative() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override
      public int weightOf(Integer value) {
        return -1;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> cache = super.<Integer, Integer>builder()
        .weigher(weigher).build();
    cache.put(1, 2);
  }

  @Test(groups = "development", expectedExceptions=IllegalArgumentException.class)
  public void weightedValue_withZero() {
    Weigher<Integer> weigher = new Weigher<Integer>() {
      @Override
      public int weightOf(Integer value) {
        return 0;
      }
    };
    ConcurrentLinkedHashMap<Integer, Integer> cache = super.<Integer, Integer>builder()
        .weigher(weigher).build();
    cache.put(1, 2);
  }

  @Test(groups = "development")
  public void weightedValue_withCollections() {
    Builder<Integer, Collection<Integer>> builder = builder();
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> cache =
        builder.weigher(Weighers.<Integer>collection()).build();

    // add first
    cache.put(1, Arrays.asList(1, 2, 3));
    assertEquals(cache.size(), 1);
    assertEquals(cache.weightedSize(), 3);

    // add second
    cache.put(2, Arrays.asList(1));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 4);

    // put as update
    cache.put(1, Arrays.asList(-4, -5, -6, -7));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 5);

    // put as update with same weight
    cache.put(1, Arrays.asList(4, 5, 6, 7));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 5);

    // replace
    cache.replace(2, Arrays.asList(-8, -9, -10));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 7);

    // replace with same weight
    cache.replace(2, Arrays.asList(8, 9, 10));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 7);

    // fail to replace conditionally
    assertFalse(cache.replace(2, Arrays.asList(-1), Arrays.asList(11, 12)));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 7);

    // replace conditionally
    assertTrue(cache.replace(2, Arrays.asList(8, 9, 10), Arrays.asList(11, 12)));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 6);

    // replace conditionally with same weight
    assertTrue(cache.replace(2, Arrays.asList(11, 12), Arrays.asList(13, 14)));
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), 6);

    // remove
    cache.remove(2);
    assertEquals(cache.size(), 1);
    assertEquals(cache.weightedSize(), 4);

    // fail to remove conditionally
    cache.remove(1, Arrays.asList(-1));
    assertEquals(cache.size(), 1);
    assertEquals(cache.weightedSize(), 4);

    // remove conditionally
    cache.remove(1, Arrays.asList(4, 5, 6, 7));
    assertEquals(cache.size(), 0);
    assertEquals(cache.weightedSize(), 0);

    // clear
    cache.put(3, Arrays.asList(1, 2, 3));
    cache.put(4, Arrays.asList(4, 5, 6));
    cache.clear();
    assertEquals(cache.size(), 0);
    assertEquals(cache.weightedSize(), 0);
  }

  @Test(groups = "development")
  public void integerOverflow() {
    final AtomicBoolean max = new AtomicBoolean(true);
    Builder<Integer, Integer> builder = builder();
    builder.weigher(new Weigher<Integer>() {
      public int weightOf(Integer value) {
        return max.get() ? ConcurrentLinkedHashMap.MAXIMUM_WEIGHT : 1;
      }
    });
    ConcurrentLinkedHashMap<Integer, Integer> cache = builder
        .maximumWeightedCapacity(ConcurrentLinkedHashMap.MAXIMUM_CAPACITY)
        .build();
    cache.put(1, 1);
    cache.put(2, 2);
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), ConcurrentLinkedHashMap.MAXIMUM_CAPACITY);
    max.set(false);
    cache.put(3, 3);
    assertEquals(cache.size(), 2);
    assertEquals(cache.weightedSize(), ConcurrentLinkedHashMap.MAXIMUM_WEIGHT + 1);
  }

  private <E> Iterable<E> asIterable(final Collection<E> c) {
    return new Iterable<E>() {
      @Override public Iterator<E> iterator() {
        return c.iterator();
      }
    };
  }
}
