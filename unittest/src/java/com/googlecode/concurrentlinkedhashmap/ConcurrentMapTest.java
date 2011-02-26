package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Maps.immutableEntry;
import static com.google.common.collect.Maps.newHashMap;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyCollection.emptyCollection;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyMap.emptyMap;
import static com.googlecode.concurrentlinkedhashmap.IsReserializable.reserializable;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang.StringUtils.countMatches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.ImmutableMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A unit-test for {@link java.util.concurrent.ConcurrentMap} interface and its
 * serializability. These tests do not assert correct concurrency behavior.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class ConcurrentMapTest extends BaseTest {

  @Override
  protected int capacity() {
    return 100;
  }

  @Test(dataProvider = "guardedMap")
  public void clear_whenEmpty(Map<Integer, Integer> map) {
    map.clear();
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void clear_whenPopulated(Map<Integer, Integer> map) {
    map.clear();
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap")
  public void size_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.size(), is(0));
  }

  @Test(dataProvider = "warmedMap")
  public void size_whenPopulated(Map<Integer, Integer> map) {
    assertThat(map.size(), is(equalTo(capacity())));
  }

  @Test(dataProvider = "guardedMap")
  public void isEmpty_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void isEmpty_whenPopulated(Map<Integer, Integer> map) {
    assertThat(map.isEmpty(), is(false));
  }

  @Test(dataProvider = "warmedMap")
  public void equals_withNull(Map<Integer, Integer> map) {
    assertThat(map, is(not(equalTo(null))));
  }

  @Test(dataProvider = "warmedMap")
  public void equals_withSelf(Map<Integer, Integer> map) {
    assertThat(map, is(equalTo(map)));
  }

  @Test(dataProvider = "guardedMap")
  public void equals_whenEmpty(Map<Object, Object> map) {
    Map<Object, Object> empty = ImmutableMap.of();
    assertThat(map, is(empty));
    assertThat(empty, is(equalTo(map)));
  }

  @Test(dataProvider = "warmedMap")
  public void equals_whenPopulated(Map<Integer, Integer> map) {
    Map<Integer, Integer> expected = ImmutableMap.copyOf(newWarmedMap());
    assertThat(map, is(equalTo(expected)));
    assertThat(expected, is(equalTo(map)));
  }

  @Test(dataProvider = "warmedMap")
  public void hashCode_withSelf(Map<Integer, Integer> map) {
    assertThat(map.hashCode(), is(equalTo(map.hashCode())));
  }

  @Test(dataProvider = "guardedMap")
  public void hashCode_withEmpty(Map<Integer, Integer> map) {
    assertThat(map.hashCode(), is(equalTo(ImmutableMap.of().hashCode())));
  }

  @Test(dataProvider = "warmedMap")
  public void hashCode_whenPopulated(Map<Integer, Integer> map) {
    Map<Integer, Integer> other = newHashMap();
    warmUp(other, 0, capacity());
    assertThat(map.hashCode(), is(equalTo(other.hashCode())));
  }

  @Test
  public void equalsAndHashCodeFails() {
    Map<Integer, Integer> empty = ImmutableMap.of();
    Map<Integer, Integer> data1 = newHashMap();
    Map<Integer, Integer> data2 = newHashMap();
    warmUp(data1, 0, 50);
    warmUp(data2, 50, 100);

    checkEqualsAndHashCodeNotEqual(empty, data2, "empty CLHM, populated other");
    checkEqualsAndHashCodeNotEqual(data1, empty, "populated CLHM, empty other");
    checkEqualsAndHashCodeNotEqual(data1, data2, "both populated");
  }

  private void checkEqualsAndHashCodeNotEqual(
      Map<Integer, Integer> first, Map<Integer, Integer> second, String errorMsg) {
    Map<Integer, Integer> map = newGuarded();
    Map<Integer, Integer> other = newHashMap();
    map.putAll(first);
    other.putAll(second);

    assertThat(errorMsg, map, is(not(equalTo(other))));
    assertThat(errorMsg, other, is(not(equalTo(map))));
    assertThat(errorMsg, map.hashCode(), is(not(equalTo(other.hashCode()))));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void containsKey_withNull(Map<Integer, Integer> map) {
    map.containsKey(null);
  }

  @Test(dataProvider = "warmedMap")
  public void containsKey_whenFound(Map<Integer, Integer> map) {
    assertThat(map.containsKey(1), is(true));
  }

  @Test(dataProvider = "warmedMap")
  public void containsKey_whenNotFound(Map<Integer, Integer> map) {
    assertThat(map.containsKey(-1), is(false));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void containsValue_withNull(Map<Integer, Integer> map) {
    map.containsValue(null);
  }

  @Test(dataProvider = "warmedMap")
  public void containsValue_whenFound(Map<Integer, Integer> map) {
    assertThat(map.containsValue(-1), is(true));
  }

  @Test(dataProvider = "warmedMap")
  public void containsValue_whenNotFound(Map<Integer, Integer> map) {
    assertThat(map.containsValue(1), is(false));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void get_withNull(Map<Integer, Integer> map) {
    map.get(null);
  }

  @Test(dataProvider = "warmedMap")
  public void get_whenFound(Map<Integer, Integer> map) {
    assertThat(map.get(1), is(-1));
  }

  @Test(dataProvider = "warmedMap")
  public void get_whenNotFound(Map<Integer, Integer> map) {
    assertThat(map.get(-1), is(nullValue()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void put_withNullKey(Map<Integer, Integer> map) {
    map.put(null, 2);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void put_withNullValue(Map<Integer, Integer> map) {
    map.put(1, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void put_withNullEntry(Map<Integer, Integer> map) {
    map.put(null, null);
  }

  @Test(dataProvider = "guardedMap")
  public void put(Map<Integer, Integer> map) {
    assertThat(map.put(1, 2), is(nullValue()));
    assertThat(map.put(1, 3), is(2));
    assertThat(map.get(1), is(3));
    assertThat(map.size(), is(1));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void putAll_withNull(Map<Integer, Integer> map) {
    map.putAll(null);
  }

  @Test(dataProvider = "guardedMap")
  public void putAll_withEmpty(Map<Integer, Integer> map) {
    map.putAll(ImmutableMap.<Integer, Integer>of());
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap")
  public void putAll_whenPopulated(Map<Integer, Integer> map) {
    Map<Integer, Integer> data = newHashMap();
    warmUp(data, 0, 50);
    map.putAll(data);
    assertThat(map, is(equalTo(data)));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullKey(ConcurrentMap<Integer, Integer> map) {
    map.putIfAbsent(1, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullValue(ConcurrentMap<Integer, Integer> map) {
    map.putIfAbsent(null, 2);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullEntry(ConcurrentMap<Integer, Integer> map) {
    map.putIfAbsent(null, null);
  }

  @Test(dataProvider = "guardedMap")
  public void putIfAbsent(ConcurrentMap<Integer, Integer> map) {
    for (Integer i = 0; i < capacity(); i++) {
      assertThat(map.putIfAbsent(i, i), is(nullValue()));
      assertThat(map.putIfAbsent(i, 1), is(i));
      assertThat(map.get(i), is(i));
    }
    assertThat(map.size(), is(equalTo(capacity())));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void remove_withNullKey(Map<Integer, Integer> map) {
    map.remove(null);
  }

  @Test(dataProvider = "guardedMap")
  public void remove_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.remove(1), is(nullValue()));
  }

  @Test(dataProvider = "guardedMap")
  public void remove(Map<Integer, Integer> map) {
    map.put(1, 2);
    assertThat(map.remove(1), is(2));
    assertThat(map.remove(1), is(nullValue()));
    assertThat(map.get(1), is(nullValue()));
    assertThat(map.containsKey(1), is(false));
    assertThat(map.containsValue(2), is(false));
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullKey(ConcurrentMap<Integer, Integer> map) {
    map.remove(null, 2);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullValue(ConcurrentMap<Integer, Integer> map) {
    map.remove(1, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullEntry(ConcurrentMap<Integer, Integer> map) {
    map.remove(null, null);
  }

  @Test(dataProvider = "guardedMap")
  public void removeConditionally_whenEmpty(ConcurrentMap<Integer, Integer> map) {
    assertThat(map.remove(1, 2), is(false));
  }

  @Test(dataProvider = "guardedMap")
  public void removeConditionally(ConcurrentMap<Integer, Integer> map) {
    map.put(1, 2);
    assertThat(map.remove(1, -2), is(false));
    assertThat(map.remove(1, 2), is(true));
    assertThat(map.get(1), is(nullValue()));
    assertThat(map.containsKey(1), is(false));
    assertThat(map.containsValue(2), is(false));
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replace_withNullKey(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, 2);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replace_withNullValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(1, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replace_withNullEntry(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, null);
  }

  @Test(dataProvider = "guardedMap")
  public void replace_whenEmpty(ConcurrentMap<Integer, Integer> map) {
    assertThat(map.replace(1, 2), is(nullValue()));
  }

  @Test(dataProvider = "guardedMap")
  public void replace_whenPopulated(ConcurrentMap<Integer, Integer> map) {
    map.put(1, 2);
    assertThat(map.replace(1, 3), is(2));
    assertThat(map.get(1), is(3));
    assertThat(map.size(), is(1));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKey(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, 2, 3);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(1, null, 3);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullNewValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(1, 2, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndOldValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, null, 3);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndNewValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, 2, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldAndNewValue(ConcurrentMap<Integer, Integer> map) {
    map.replace(1, null, null);
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndValues(ConcurrentMap<Integer, Integer> map) {
    map.replace(null, null, null);
  }

  @Test(dataProvider = "guardedMap")
  public void replaceConditionally_whenEmpty(ConcurrentMap<Integer, Integer> map) {
    assertThat(map.replace(1, 2, 3), is(false));
  }

  @Test(dataProvider = "guardedMap")
  public void replaceConditionally_whenPopulated(ConcurrentMap<Integer, Integer> map) {
    map.put(1, 2);
    assertThat(map.replace(1, 3, 4), is(false));
    assertThat(map.replace(1, 2, 3), is(true));
    assertThat(map.get(1), is(3));
    assertThat(map.size(), is(1));
  }

  @Test(dataProvider = "guardedMap")
  public void toString_whenempty(Map<Integer, Integer> map) {
    assertThat(map, hasToString(ImmutableMap.of().toString()));
  }

  @Test(dataProvider = "guardedMap")
  public void toString_whenPopulated(Map<Integer, Integer> map) {
    warmUp(map, 0, 10);
    String toString = map.toString();
    for (Entry<Integer, Integer> entry : map.entrySet()) {
      assertThat(countMatches(toString, entry.toString()), is(equalTo(1)));
    }
  }

  @Test(dataProvider = "guardedMap")
  public void serialization_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map, is(reserializable()));
  }

  @Test(dataProvider = "warmedMap")
  public void serialization_whenPopulated(Map<Integer, Integer> map) {
    assertThat(map, is(reserializable()));
  }

  @Test(dataProvider = "guardingListener")
  public void serialize_withCustomSettings(
      EvictionListener<Integer, Collection<Integer>> listener) {
    Map<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
          .weigher(Weighers.<Integer>collection())
          .maximumWeightedCapacity(500)
          .initialCapacity(100)
          .concurrencyLevel(32)
          .listener(listener)
          .build();
    map.put(1, singletonList(2));
    assertThat(map, is(reserializable()));
  }

  /* ---------------- Key Set -------------- */

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void keySetToArray_withNull(Map<Integer, Integer> map) {
    map.keySet().toArray(null);
  }

  @Test(dataProvider = "guardedMap")
  public void keySetToArray_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.keySet().toArray(new Integer[0]).length, is(equalTo(0)));
    assertThat(map.keySet().toArray().length, is(equalTo(0)));
  }

  @Test(dataProvider = "warmedMap")
  public void keySetToArray_whenPopulated(Map<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();
    Object[] array1 = keys.toArray();
    Object[] array2 = keys.toArray(new Integer[map.size()]);
    Object[] expected = newHashMap(map).keySet().toArray();
    for (Object[] array : asList(array1, array2)) {
      assertThat(array.length, is(equalTo(keys.size())));
      assertThat(asList(array), containsInAnyOrder(expected));
    }
  }

  @Test(dataProvider = "guardedMap")
  public void keySet_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.keySet(), is(emptyCollection()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = UnsupportedOperationException.class)
  public void keySet_addNotSupported(Map<Integer, Integer> map) {
    map.keySet().add(1);
  }

  @Test(dataProvider = "warmedMap")
  public void keySet_withClear(Map<Integer, Integer> map) {
    map.keySet().clear();
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void keySet_whenPopulated(Map<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();
    assertThat(keys.contains(new Object()), is(false));
    assertThat(keys.remove(new Object()), is(false));
    assertThat(keys, hasSize(capacity()));
    for (int i = 0; i < capacity(); i++) {
      assertThat(keys.contains(i), is(true));
      assertThat(keys.remove(i), is(true));
      assertThat(keys.remove(i), is(false));
      assertThat(keys.contains(i), is(false));
    }
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void keySet_iterator(Map<Integer, Integer> map) {
    int iterations = 0;
    Set<Integer> keys = map.keySet();
    for (Iterator<Integer> i = map.keySet().iterator(); i.hasNext();) {
      assertThat(map.containsKey(i.next()), is(true));
      iterations++;
      i.remove();
    }
    assertThat(iterations, is(equalTo(capacity())));
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = IllegalStateException.class)
  public void keyIterator_noElement(Map<Integer, Integer> map) {
    map.keySet().iterator().remove();
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NoSuchElementException.class)
  public void keyIterator_noMoreElements(Map<Integer, Integer> map) {
    map.keySet().iterator().next();
  }

  /* ---------------- Values -------------- */

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void valuesToArray_withNull(Map<Integer, Integer> map) {
    map.values().toArray(null);
  }

  @Test(dataProvider = "guardedMap")
  public void valuesToArray_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.values().toArray(new Integer[0]).length, is(equalTo(0)));
    assertThat(map.values().toArray().length, is(equalTo(0)));
  }

  @Test(dataProvider = "warmedMap")
  public void valuesToArray_whenPopulated(Map<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    Object[] array1 = values.toArray();
    Object[] array2 = values.toArray(new Integer[map.size()]);
    Object[] expected = newHashMap(map).values().toArray();
    for (Object[] array : asList(array1, array2)) {
      assertThat(array.length, is(equalTo(values.size())));
      assertThat(asList(array), containsInAnyOrder(expected));
    }
  }

  @Test(dataProvider = "guardedMap")
  public void values_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.values(), is(emptyCollection()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = UnsupportedOperationException.class)
  public void values_addNotSupported(Map<Integer, Integer> map) {
    map.values().add(1);
  }

  @Test(dataProvider = "warmedMap")
  public void values_withClear(Map<Integer, Integer> map) {
    map.values().clear();
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void values_whenPopulated(Map<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    assertThat(values.contains(new Object()), is(false));
    assertThat(values.remove(new Object()), is(false));
    assertThat(values, hasSize(capacity()));
    for (int i = 0; i < capacity(); i++) {
      assertThat(values.contains(-i), is(true));
      assertThat(values.remove(-i), is(true));
      assertThat(values.remove(-i), is(false));
      assertThat(values.contains(-i), is(false));
    }
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void valueIterator(Map<Integer, Integer> map) {
    int iterations = 0;
    Collection<Integer> values = map.values();
    for (Iterator<Integer> i = map.values().iterator(); i.hasNext();) {
      assertThat(map.containsValue(i.next()), is(true));
      iterations++;
      i.remove();
    }
    assertThat(iterations, is(equalTo(capacity())));
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = IllegalStateException.class)
  public void valueIterator_noElement(Map<Integer, Integer> map) {
    map.values().iterator().remove();
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NoSuchElementException.class)
  public void valueIterator_noMoreElements(Map<Integer, Integer> map) {
    map.values().iterator().next();
  }

  /* ---------------- Entry Set -------------- */

  @Test(dataProvider = "guardedMap", expectedExceptions = NullPointerException.class)
  public void entrySetToArray_withNull(Map<Integer, Integer> map) {
    map.entrySet().toArray(null);
  }

  @Test(dataProvider = "guardedMap")
  public void entrySetToArray_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.entrySet().toArray(new Integer[0]).length, is(equalTo(0)));
    assertThat(map.entrySet().toArray().length, is(equalTo(0)));
  }

  @Test(dataProvider = "warmedMap")
  public void entrySetToArray_whenPopulated(Map<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    Object[] array1 = entries.toArray();
    Object[] array2 = entries.toArray(new Entry[map.size()]);
    Object[] expected = newHashMap(map).entrySet().toArray();
    for (Object[] array : asList(array1, array2)) {
      assertThat(array.length, is(equalTo(entries.size())));
      assertThat(asList(array), containsInAnyOrder(expected));
    }
  }

  @Test(dataProvider = "guardedMap")
  public void entrySet_whenEmpty(Map<Integer, Integer> map) {
    assertThat(map.entrySet(), is(emptyCollection()));
  }

  @Test(dataProvider = "guardedMap")
  public void entrySet_addIsSupported(Map<Integer, Integer> map) {
    assertThat(map.entrySet().add(immutableEntry(1, 2)), is(true));
    assertThat(map.entrySet().add(immutableEntry(1, 2)), is(false));
    assertThat(map.entrySet().size(), is(1));
    assertThat(map.size(), is(1));
  }

  @Test(dataProvider = "warmedMap")
  public void entrySet_withClear(Map<Integer, Integer> map) {
    map.entrySet().clear();
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void entrySet_whenPopulated(Map<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    assertThat(entries.contains(immutableEntry(entry.getKey(), entry.getValue() + 1)), is(false));
    assertThat(entries.contains(new Object()), is(false));
    assertThat(entries.remove(new Object()), is(false));
    assertThat(entries, hasSize(capacity()));
    for (int i = 0; i < capacity(); i++) {
      Entry<Integer, Integer> newEntry = immutableEntry(i, -i);
      assertThat(entries.contains(newEntry), is(true));
      assertThat(entries.remove(newEntry), is(true));
      assertThat(entries.remove(newEntry), is(false));
      assertThat(entries.contains(newEntry), is(false));
    }
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "warmedMap")
  public void entryIterator(Map<Integer, Integer> map) {
    int iterations = 0;
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    for (Iterator<Entry<Integer, Integer>> i = map.entrySet().iterator(); i.hasNext();) {
      Entry<Integer, Integer> entry = i.next();
      assertThat(map, hasEntry(entry.getKey(), entry.getValue()));
      iterations++;
      i.remove();
    }
    assertThat(iterations, is(equalTo(capacity())));
    assertThat(map, is(emptyMap()));
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = IllegalStateException.class)
  public void entryIterator_noElement(Map<Integer, Integer> map) {
    map.entrySet().iterator().remove();
  }

  @Test(dataProvider = "guardedMap", expectedExceptions = NoSuchElementException.class)
  public void entryIterator_noMoreElements(Map<Integer, Integer> map) {
    map.entrySet().iterator().next();
  }

  @Test(dataProvider = "guardedMap")
  public void writeThroughEntry(Map<Integer, Integer> map) {
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();

    map.remove(1);
    assertThat(map, is(emptyMap()));

    entry.setValue(3);
    assertThat(map.size(), is(1));
    assertThat(map.get(1), is(3));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = NullPointerException.class)
  public void writeThroughEntry_withNull(Map<Integer, Integer> map) {
    map.entrySet().iterator().next().setValue(null);
  }

  @Test(dataProvider = "warmedMap")
  public void writeThroughEntry_serialize(Map<Integer, Integer> map) {
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    assertThat(entry, is(reserializable()));
  }
}
