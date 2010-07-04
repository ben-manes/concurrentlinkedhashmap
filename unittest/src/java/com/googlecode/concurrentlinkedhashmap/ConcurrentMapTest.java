package com.googlecode.concurrentlinkedhashmap;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

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

  @Test(dataProvider = "guarded")
  public void clear_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.clear();
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void clear_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.clear();
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void size_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.size(), 0);
  }

  @Test(dataProvider = "warmed")
  public void size_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.size(), capacity());
  }

  @Test(dataProvider = "guarded")
  public void isEmpty_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertTrue(map.isEmpty());
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void isEmpty_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.isEmpty());
  }

  @Test(dataProvider = "warmed")
  public void equals_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.equals(null));
  }

  @Test(dataProvider = "warmed")
  public void equals_withSelf(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map, map);
  }

  @Test(dataProvider = "guarded")
  public void equals_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map, Collections.emptyMap());
    assertEquals(Collections.emptyMap(), map);
  }

  @Test(dataProvider = "warmed")
  public void equals_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = unmodifiableMap(newWarmedMap());
    assertEquals(map, expected);
    assertEquals(expected, map);
  }

  @Test(dataProvider = "warmed")
  public void hashCode_withSelf(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.hashCode(), map.hashCode());
  }

  @Test(dataProvider = "guarded")
  public void hashCode_withEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.hashCode(), Collections.emptyMap().hashCode());
  }

  @Test(dataProvider = "guarded")
  public void hashCode_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> other = new HashMap<Integer, Integer>();
    warmUp(map, 0, 50);
    warmUp(other, 0, 50);
    assertEquals(map.hashCode(), other.hashCode());
  }

  @Test
  public void equalsAndHashCodeFails() {
    Map<Integer, Integer> data1 = new HashMap<Integer, Integer>();
    Map<Integer, Integer> data2 = new HashMap<Integer, Integer>();
    Map<Integer, Integer> empty = Collections.emptyMap();
    warmUp(data1, 0, 50);
    warmUp(data2, 50, 100);

    checkEqualsAndHashCodeNotEqual(empty, data2, "empty CLHM, populated other");
    checkEqualsAndHashCodeNotEqual(data1, empty, "populated CLHM, empty other");
    checkEqualsAndHashCodeNotEqual(data1, data2, "both populated");
  }

  private void checkEqualsAndHashCodeNotEqual(Map<Integer, Integer> first,
      Map<Integer, Integer> second, String errorMsg) {
    ConcurrentLinkedHashMap<Integer, Integer> map = newGuarded();
    Map<Integer, Integer> other = new HashMap<Integer, Integer>();
    map.putAll(first);
    other.putAll(second);

    assertFalse(map.equals(other), errorMsg);
    assertFalse(other.equals(map), errorMsg);
    assertFalse(map.hashCode() == other.hashCode(), errorMsg);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void containsKey_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.containsKey(null);
  }

  @Test(dataProvider = "guarded")
  public void containsKey_whenFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertTrue(map.containsKey(1));
  }

  @Test(dataProvider = "guarded")
  public void containsKey_whenNotFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertFalse(map.containsKey(2));
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void containsValue_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.containsValue(null);
  }

  @Test(dataProvider = "guarded")
  public void containsValue_whenFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertTrue(map.containsValue(2));
  }

  @Test(dataProvider = "guarded")
  public void containsValue_whenNotFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertFalse(map.containsValue(1));
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void get_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.get(null);
  }

  @Test(dataProvider = "guarded")
  public void get_whenFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertEquals(map.get(1), Integer.valueOf(2));
  }

  @Test(dataProvider = "guarded")
  public void get_whenNotFound(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertNull(map.get(2));
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void put_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void put_withNullValue(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(null, 2);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void put_withNullEntry(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(null, null);
  }

  @Test(dataProvider = "guarded")
  public void put(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertNull(map.put(1, 2));
    assertEquals(map.put(1, 3), Integer.valueOf(2));
    assertEquals(map.get(1), Integer.valueOf(3));
    assertEquals(1, map.size());
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void putAll_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.putAll(null);
  }

  @Test(dataProvider = "guarded")
  public void putAll_withEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.putAll(Collections.<Integer, Integer>emptyMap());
    assertEquals(map, emptyMap());
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void putAll_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> data = new HashMap<Integer, Integer>();
    warmUp(data, 0, 50);
    map.putAll(data);
    assertEquals(map, data);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.putIfAbsent(1, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullValue(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.putIfAbsent(null, 2);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullEntry(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.putIfAbsent(null, null);
  }

  @Test(dataProvider = "guarded")
  public void putIfAbsent(ConcurrentLinkedHashMap<Integer, Integer> map) {
    for (Integer i = 0; i < capacity(); i++) {
      assertNull(map.putIfAbsent(i, i));
      assertEquals(map.putIfAbsent(i, -1), i);
      assertEquals(map.get(i), i);
    }
    assertEquals(map.size(), capacity());
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void remove_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.remove(null);
  }

  @Test(dataProvider = "guarded")
  public void remove_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertNull(map.remove(1));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void remove(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertEquals(map.remove(1), Integer.valueOf(2));
    assertNull(map.remove(1));
    assertNull(map.get(1));
    assertFalse(map.containsKey(1));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.remove(null, 2);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullValue(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.remove(1, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullEntry(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.remove(null, null);
  }

  @Test(dataProvider = "guarded")
  public void removeConditionally_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.remove(1, 2));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void removeConditionally(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertFalse(map.remove(1, -2));
    assertTrue(map.remove(1, 2));
    assertNull(map.get(1));
    assertFalse(map.containsKey(1));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replace_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, 2);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replace_withNullValue(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(1, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replace_withNullEntry(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, null);
  }

  @Test(dataProvider = "guarded")
  public void replace_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertNull(map.replace(1, 2));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void replace_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertEquals(Integer.valueOf(2), map.replace(1, 3));
    assertEquals(Integer.valueOf(3), map.get(1));
    assertEquals(1, map.size());
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKey(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, 2, 3);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldValue(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(1, null, 3);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullNewValue(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(1, 2, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndOldValue(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, null, 3);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndNewValue(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, 2, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldAndNewValue(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(1, null, null);
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndValues(
      ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.replace(null, null, null);
  }

  @Test(dataProvider = "guarded")
  public void replaceConditionally_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.replace(1, 2, 3));
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded")
  public void replaceConditionally_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    assertFalse(map.replace(1, 3, 4));
    assertTrue(map.replace(1, 2, 3));
    assertEquals(map.get(1), Integer.valueOf(3));
    assertEquals(map.size(), 1);
  }

  @Test(dataProvider = "guarded")
  public void toString_whenempty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.toString(), emptyMap().toString());
  }

  @Test(dataProvider = "guarded")
  public void toString_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    warmUp(map, 0, 5);
    String str = map.toString();
    for (int i=1; i < 5; i++) {
      assertEquals(StringUtils.countMatches(str, i + "=" + -i), 1);
    }
  }

  @Test(dataProvider = "guarded")
  public void serialization_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    ConcurrentLinkedHashMap<Integer, Integer> copy = cloneAndAssert(map);
    validator.checkEmpty(copy);
  }

  @Test(dataProvider = "warmed")
  public void serialization_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    cloneAndAssert(map);
  }

  @Test(dataProvider = "guardingListener")
  public void serialize_withCustomSettings(
      EvictionListener<Integer, Collection<Integer>> listener) {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
          .weigher(Weighers.<Integer>collection())
          .maximumWeightedCapacity(500)
          .initialCapacity(100)
          .concurrencyLevel(32)
          .listener(listener)
          .build();
    map.put(1, singletonList(2));
    cloneAndAssert(map);
  }

  @SuppressWarnings("unchecked")
  private <K, V> ConcurrentLinkedHashMap<K, V> cloneAndAssert(
      ConcurrentLinkedHashMap<K, V> map) {
    ConcurrentLinkedHashMap<K, V> copy =
      (ConcurrentLinkedHashMap<K, V>) SerializationUtils.clone(map);
    assertEquals(copy.maximumWeightedSize, map.maximumWeightedSize);
    assertEquals(copy.listener.getClass(), map.listener.getClass());
    assertEquals(copy.weigher.getClass(), map.weigher.getClass());
    assertEquals(copy.concurrencyLevel, map.concurrencyLevel);
    validator.checkEqualsAndHashCode(map, copy);
    validator.checkValidState(map);
    validator.checkValidState(copy);
    return copy;
  }

  /* ---------------- Key Set -------------- */

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void keySetToArray_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.keySet().toArray(null);
  }

  @Test(dataProvider = "guarded")
  public void keySetToArray_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.keySet().toArray(new Integer[0]).length, 0);
    assertEquals(map.keySet().toArray().length, 0);
  }

  @Test(dataProvider = "warmed")
  public void keySetToArray_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();

    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).keySet().toArray());
    assertTrue(asList(keys.toArray(new Integer[map.size()])).containsAll(expectedArray));
    assertEquals(keys.size(), keys.toArray(new Integer[map.size()]).length);
    assertTrue(asList(keys.toArray()).containsAll(expectedArray));
    assertEquals(keys.size(), keys.toArray().length);
  }

  @Test(dataProvider = "guarded")
  public void keySet_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    validator.checkEmpty(map.keySet());
  }

  @Test(dataProvider = "guarded", expectedExceptions = UnsupportedOperationException.class)
  public void keySet_addNotSupported(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.keySet().add(1));
  }

  @Test(dataProvider = "guarded")
  public void keySet_withClear(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();
    keys.clear();
    validator.checkEmpty(keys);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void keySet_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();
    int size = map.size();

    assertFalse(keys.contains(new Object()));
    assertFalse(keys.remove(new Object()));
    assertEquals(keys.size(), size);
    for (int i=0; i<size; i++) {
      assertTrue(keys.contains(i));
      assertTrue(keys.remove(i));
      assertFalse(keys.remove(i));
      assertFalse(keys.contains(i));
    }
    validator.checkEmpty(keys);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void keySet_iterator(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Integer> keys = map.keySet();
    int size = map.size();

    int iterations = 0;
    for (Iterator<Integer> i=map.keySet().iterator(); i.hasNext();) {
      map.containsKey(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, size);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded", expectedExceptions = IllegalStateException.class)
  public void keyIterator_noElement(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.keySet().iterator().remove();
  }

  @Test(dataProvider = "guarded", expectedExceptions = NoSuchElementException.class)
  public void keyIterator_noMoreElements(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.keySet().iterator().next();
  }

  /* ---------------- Values -------------- */

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void valuesToArray_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.values().toArray(null);
  }

  @Test(dataProvider = "guarded")
  public void valuesToArray_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.values().toArray(new Integer[0]).length, 0);
    assertEquals(map.values().toArray().length, 0);
  }

  @Test(dataProvider = "warmed")
  public void valuesToArray_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).values().toArray());
    assertTrue(asList(values.toArray(new Integer[map.size()])).containsAll(expectedArray));
    assertEquals(values.size(), values.toArray(new Integer[map.size()]).length);
    assertTrue(asList(values.toArray()).containsAll(expectedArray));
    assertEquals(values.size(), values.toArray().length);
  }

  @Test(dataProvider = "guarded")
  public void values_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    validator.checkEmpty(map.values());
  }

  @Test(dataProvider = "guarded", expectedExceptions = UnsupportedOperationException.class)
  public void values_addNotSupported(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertFalse(map.values().add(1));
  }

  @Test(dataProvider = "warmed")
  public void values_withClear(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    values.clear();
    validator.checkEmpty(values);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void values_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    int size = map.size();

    assertFalse(values.contains(new Object()));
    assertFalse(values.remove(new Object()));
    assertEquals(values.size(), size);
    for (int i=0; i<size; i++) {
      assertTrue(values.contains(-i));
      assertTrue(values.remove(-i));
      assertFalse(values.remove(-i));
      assertFalse(values.contains(-i));
    }
    validator.checkEmpty(values);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void valueIterator(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Collection<Integer> values = map.values();
    int size = map.size();

    int iterations = 0;
    for (Iterator<Integer> i=map.values().iterator(); i.hasNext();) {
      map.containsValue(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, size);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded", expectedExceptions = IllegalStateException.class)
  public void valueIterator_noElement(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.values().iterator().remove();
  }

  @Test(dataProvider = "guarded", expectedExceptions = NoSuchElementException.class)
  public void valueIterator_noMoreElements(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.values().iterator().next();
  }

  /* ---------------- Entry Set -------------- */

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void entrySetToArray_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.entrySet().toArray(null);
  }

  @Test(dataProvider = "guarded")
  public void entrySetToArray_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertEquals(map.entrySet().toArray(new Integer[0]).length, 0);
    assertEquals(map.entrySet().toArray().length, 0);
  }

  @Test(dataProvider = "warmed")
  public void entrySetToArray_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();

    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).entrySet().toArray());
    assertTrue(asList(entries.toArray(new Entry[map.size()])).containsAll(expectedArray));
    assertEquals(entries.size(), entries.toArray(new Entry[map.size()]).length);
    assertTrue(asList(entries.toArray()).containsAll(expectedArray));
    assertEquals(entries.size(), entries.toArray().length);
  }

  @Test(dataProvider = "guarded")
  public void entrySet_whenEmpty(ConcurrentLinkedHashMap<Integer, Integer> map) {
    validator.checkEmpty(map.entrySet());
  }

  @Test(dataProvider = "guarded")
  public void entrySet_addIsSupported(ConcurrentLinkedHashMap<Integer, Integer> map) {
    assertTrue(map.entrySet().add(new SimpleEntry<Integer, Integer>(1, 2)));
    assertFalse(map.entrySet().add(new SimpleEntry<Integer, Integer>(1, 2)));
    assertEquals(map.entrySet().size(), 1);
    assertEquals(map.size(), 1);
  }

  @Test(dataProvider = "warmed")
  public void entrySet_withClear(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    entries.clear();
    validator.checkEmpty(entries);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void entrySet_whenPopulated(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    int size = map.size();

    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    assertFalse(entries.contains(
        new SimpleEntry<Integer, Integer>(entry.getKey(), entry.getValue() + 1)));
    assertFalse(entries.contains(new Object()));
    assertFalse(entries.remove(new Object()));
    assertEquals(entries.size(), size);
    for (int i=0; i<size; i++) {
      Entry<Integer, Integer> newEntry = new SimpleEntry<Integer, Integer>(i, -i);
      assertTrue(entries.contains(newEntry));
      assertTrue(entries.remove(newEntry));
      assertFalse(entries.remove(newEntry));
      assertFalse(entries.contains(newEntry));
    }

    validator.checkEmpty(entries);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "warmed")
  public void entryIterator(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    int size = map.size();

    int iterations = 0;
    for (Iterator<Entry<Integer, Integer>> i=map.entrySet().iterator(); i.hasNext();) {
      map.containsValue(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, size);
    validator.checkEmpty(map);
  }

  @Test(dataProvider = "guarded", expectedExceptions = IllegalStateException.class)
  public void entryIterator_noElement(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.entrySet().iterator().remove();
  }

  @Test(dataProvider = "guarded", expectedExceptions = NoSuchElementException.class)
  public void entryIterator_noMoreElements(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.entrySet().iterator().next();
  }

  @Test(dataProvider = "guarded")
  public void writeThroughEntry(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();

    map.remove(1);
    assertTrue(map.isEmpty());

    entry.setValue(3);
    assertEquals(1, map.size());
    assertEquals(Integer.valueOf(3), map.get(1));
  }

  @Test(dataProvider = "guarded", expectedExceptions = NullPointerException.class)
  public void writeThroughEntry_withNull(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    entry.setValue(null);
  }

  @Test(dataProvider = "guarded")
  public void writeThroughEntry_serialize(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    Object copy = SerializationUtils.clone((Serializable) entry);
    validator.checkEqualsAndHashCode(entry, copy);
  }
}
