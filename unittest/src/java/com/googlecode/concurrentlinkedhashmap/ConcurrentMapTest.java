package com.googlecode.concurrentlinkedhashmap;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import org.apache.commons.lang.SerializationUtils;
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
public final class ConcurrentMapTest extends BaseTest {

  @Test(groups = "development")
  public void clear_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.clear();
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void clear_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    map.clear();
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void size_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.size(), 0);
  }

  @Test(groups = "development")
  public void size_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertEquals(map.size(), capacity);
  }

  @Test(groups = "development")
  public void isEmpty_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertTrue(map.isEmpty());
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void isEmpty_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertFalse(map.isEmpty());
  }

  @Test(groups = "development")
  public void equals_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertFalse(map.equals(null));
  }

  @Test(groups = "development")
  public void equals_withSelf() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertEquals(map, map);
  }

  @Test(groups = "development")
  public void equals_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map, Collections.emptyMap());
    assertEquals(Collections.emptyMap(), map);
  }

  @Test(groups = "development")
  public void equals_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertEquals(map, createWarmedMap());
    assertEquals(createWarmedMap(), map);
  }

  @Test(groups = "development")
  public void hashCode_withSelf() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createWarmedMap();
    assertEquals(map.hashCode(), map.hashCode());
  }

  @Test(groups = "development")
  public void hashCode_withEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.hashCode(), Collections.emptyMap().hashCode());
  }

  @Test(groups = "development")
  public void hashCode_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Map<Integer, Integer> other = new HashMap<Integer, Integer>();
    warmUp(map, 0, 50);
    warmUp(other, 0, 50);
    assertEquals(map.hashCode(), other.hashCode());
  }

  @Test(groups = "development")
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
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Map<Integer, Integer> other = new HashMap<Integer, Integer>();
    map.putAll(first);
    other.putAll(second);

    assertFalse(map.equals(other), errorMsg);
    assertFalse(other.equals(map), errorMsg);
    assertFalse(map.hashCode() == other.hashCode(), errorMsg
        + ":\n" + map + "\n" + second);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void containsKey_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.containsKey(null);
  }

  @Test(groups = "development")
  public void containsKey_whenFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertTrue(map.containsKey(1));
    validator.checkValidState(map);
  }

  @Test(groups = "development")
  public void containsKey_whenNotFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertFalse(map.containsKey(2));
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void containsValue_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.containsValue(null);
  }

  @Test(groups = "development")
  public void containsValue_whenFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertTrue(map.containsValue(2));
    validator.checkValidState(map);
  }

  @Test(groups = "development")
  public void containsValue_whenNotFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertFalse(map.containsValue(1));
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void get_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.get(null);
  }

  @Test(groups = "development")
  public void get_whenFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertEquals(map.get(1), Integer.valueOf(2));
    validator.checkValidState(map);
  }

  @Test(groups = "development")
  public void get_whenNotFound() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertNull(map.get(2));
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void put_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void put_withNullValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(null, 2);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void put_withNullEntry() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(null, null);
  }

  @Test(groups = "development")
  public void put() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertNull(map.put(1, 2));
    assertEquals(map.put(1, 3), Integer.valueOf(2));
    assertEquals(map.get(1), Integer.valueOf(3));
    assertEquals(1, map.size());
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void putAll_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.putAll(null);
  }

  @Test(groups = "development")
  public void putAll_withEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.putAll(Collections.<Integer, Integer>emptyMap());
    assertEquals(map, Collections.emptyMap());
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void putAll_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Map<Integer, Integer> data = new HashMap<Integer, Integer>();
    warmUp(data, 0, 50);
    map.putAll(data);
    assertEquals(map, data);
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.putIfAbsent(1, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.putIfAbsent(null, 2);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void putIfAbsent_withNullEntry() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.putIfAbsent(null, null);
  }

  @Test(groups = "development")
  public void putIfAbsent() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    for (Integer i = 0; i < capacity; i++) {
      assertNull(map.putIfAbsent(i, i));
      assertEquals(map.putIfAbsent(i, -1), i);
      assertEquals(map.get(i), i);
    }
    assertEquals(map.size(), capacity);
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void remove_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.remove(null);
  }

  @Test(groups = "development")
  public void remove_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertNull(map.remove(1));
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void remove() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertEquals(map.remove(1), Integer.valueOf(2));
    assertNull(map.remove(1));
    assertNull(map.get(1));
    assertFalse(map.containsKey(1));
    validator.checkEmpty(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.remove(null, 2);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.remove(1, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void removeConditionally_withNullEntry() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.remove(null, null);
  }

  @Test(groups = "development")
  public void removeConditionally_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertFalse(map.remove(1, 2));
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void removeConditionally() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertFalse(map.remove(1, -2));
    assertTrue(map.remove(1, 2));
    assertNull(map.get(1));
    assertFalse(map.containsKey(1));
    validator.checkEmpty(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replace_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, 2);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replace_withNullValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(1, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replace_withNullEntry() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, null);
  }

  @Test(groups = "development")
  public void replace_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertNull(map.replace(1, 2));
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void replace_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertEquals(Integer.valueOf(2), map.replace(1, 3));
    assertEquals(Integer.valueOf(3), map.get(1));
    assertEquals(1, map.size());
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKey() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, 2, 3);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(1, null, 3);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullNewValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(1, 2, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndOldValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, null, 3);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndNewValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, 2, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullOldAndNewValue() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(1, null, null);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void replaceConditionally_withNullKeyAndValues() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.replace(null, null, null);
  }

  @Test(groups = "development")
  public void replaceConditionally_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertFalse(map.replace(1, 2, 3));
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void replaceConditionally_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    assertFalse(map.replace(1, 3, 4));
    assertTrue(map.replace(1, 2, 3));
    assertEquals(map.get(1), Integer.valueOf(3));
    assertEquals(map.size(), 1);
    validator.checkValidState(map);
  }

  @Test(groups = "development")
  public void toString_whenempty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.toString(), emptyMap().toString());
  }

  @Test(groups = "development")
  public void toString_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    warmUp(map, 0, 5);
    String s = map.toString();
    for (int i=1; i < 5; i++) {
      assertTrue(s.contains(i + "=" + -i));
    }
  }

  @Test(groups = "development")
  public void serialization_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    ConcurrentLinkedHashMap<Integer, Integer> copy = cloneAndAssert(map);
    validator.checkEmpty(copy);
  }

  @Test(groups = "development")
  public void serialization_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    warmUp(map, 0, 100);
    cloneAndAssert(map);
  }

  @Test(groups = "development")
  public void serialize_withCustomSettings() {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
          .listener(EvictionMonitor.<Integer, Collection<Integer>>newGuard())
          .weigher(Weighers.<Integer>collection())
          .maximumWeightedCapacity(500)
          .initialCapacity(100)
          .concurrencyLevel(32)
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
    assertEquals(copy.concurrencyLevel, map.concurrencyLevel);
    assertEquals(copy.weigher, map.weigher);
    assertEquals(map.weigher, map.weigher);
    validator.checkEqualsAndHashCode(map, copy);
    validator.checkValidState(map);
    validator.checkValidState(copy);
    return copy;
  }

  /* ---------------- Key Set -------------- */

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void keySetToArray_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.keySet().toArray(null);
  }

  @Test(groups = "development")
  public void keySetToArray_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.keySet().toArray(new Integer[0]).length, 0);
    assertEquals(map.keySet().toArray().length, 0);
  }

  @Test(groups = "development")
  public void keySetToArray_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Integer> keys = map.keySet();
    warmUp(map, 0, 100);

    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).keySet().toArray());
    assertTrue(asList(keys.toArray(new Integer[map.size()])).containsAll(expectedArray));
    assertEquals(keys.size(), keys.toArray(new Integer[map.size()]).length);
    assertTrue(asList(keys.toArray()).containsAll(expectedArray));
    assertEquals(keys.size(), keys.toArray().length);
  }

  @Test(groups = "development")
  public void keySet_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    validator.checkEmpty(map.keySet());
  }

  @Test(groups = "development", expectedExceptions = UnsupportedOperationException.class)
  public void keySet_addNotSupported() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertFalse(map.keySet().add(1));
  }

  @Test(groups = "development")
  public void keySet_withClear() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    warmUp(map, 0, 100);

    Set<Integer> keys = map.keySet();
    keys.clear();
    validator.checkEmpty(keys);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void keySet_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Integer> keys = map.keySet();
    warmUp(map, 0, 100);

    assertFalse(keys.contains(new Object()));
    assertFalse(keys.remove(new Object()));
    assertEquals(keys.size(), 100);
    for (int i=0; i<100; i++) {
      assertTrue(keys.contains(i));
      assertTrue(keys.remove(i));
      assertFalse(keys.remove(i));
      assertFalse(keys.contains(i));
    }
    validator.checkEmpty(keys);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void keySet_iterator() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Integer> keys = map.keySet();
    warmUp(map, 0, 100);

    int iterations = 0;
    for (Iterator<Integer> i=map.keySet().iterator(); i.hasNext();) {
      map.containsKey(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, 100);
    validator.checkEmpty(map);
  }

  @Test(groups = "development", expectedExceptions = IllegalStateException.class)
  public void keyIterator_noElement() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.keySet().iterator().remove();
  }

  @Test(groups = "development", expectedExceptions = NoSuchElementException.class)
  public void keyIterator_noMoreElements() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.keySet().iterator().next();
  }

  /* ---------------- Values -------------- */

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void valuesToArray_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.values().toArray(null);
  }

  @Test(groups = "development")
  public void valuesToArray_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.values().toArray(new Integer[0]).length, 0);
    assertEquals(map.values().toArray().length, 0);
  }

  @Test(groups = "development")
  public void valuesToArray_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Collection<Integer> values = map.values();
    warmUp(map, 0, 100);

    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).values().toArray());
    assertTrue(asList(values.toArray(new Integer[map.size()])).containsAll(expectedArray));
    assertEquals(values.size(), values.toArray(new Integer[map.size()]).length);
    assertTrue(asList(values.toArray()).containsAll(expectedArray));
    assertEquals(values.size(), values.toArray().length);
  }

  @Test(groups = "development")
  public void values_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    validator.checkEmpty(map.values());
  }

  @Test(groups = "development", expectedExceptions = UnsupportedOperationException.class)
  public void values_addNotSupported() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertFalse(map.values().add(1));
  }

  @Test(groups = "development")
  public void values_withClear() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    warmUp(map, 0, 100);

    Collection<Integer> values = map.values();
    values.clear();
    validator.checkEmpty(values);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void values_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Collection<Integer> values = map.values();
    warmUp(map, 0, 100);

    assertFalse(values.contains(new Object()));
    assertFalse(values.remove(new Object()));
    assertEquals(values.size(), 100);
    for (int i=0; i<100; i++) {
      assertTrue(values.contains(-i));
      assertTrue(values.remove(-i));
      assertFalse(values.remove(-i));
      assertFalse(values.contains(-i));
    }
    validator.checkEmpty(values);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void valueIterator() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Collection<Integer> values = map.values();
    warmUp(map, 0, 100);

    int iterations = 0;
    for (Iterator<Integer> i=map.values().iterator(); i.hasNext();) {
      map.containsValue(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, 100);
    validator.checkEmpty(map);
  }

  @Test(groups = "development", expectedExceptions = IllegalStateException.class)
  public void valueIterator_noElement() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.values().iterator().remove();
  }

  @Test(groups = "development", expectedExceptions = NoSuchElementException.class)
  public void valueIterator_noMoreElements() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.values().iterator().next();
  }

  /* ---------------- Entry Set -------------- */

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void entrySetToArray_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.entrySet().toArray(null);
  }

  @Test(groups = "development")
  public void entrySetToArray_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertEquals(map.entrySet().toArray(new Integer[0]).length, 0);
    assertEquals(map.entrySet().toArray().length, 0);
  }

  @Test(groups = "development")
  public void entrySetToArray_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    warmUp(map, 0, 100);

    List<Object> expectedArray = asList(new HashMap<Integer, Integer>(map).entrySet().toArray());
    assertTrue(asList(entries.toArray(new Entry[map.size()])).containsAll(expectedArray));
    assertEquals(entries.size(), entries.toArray(new Entry[map.size()]).length);
    assertTrue(asList(entries.toArray()).containsAll(expectedArray));
    assertEquals(entries.size(), entries.toArray().length);
  }

  @Test(groups = "development")
  public void entrySet_whenEmpty() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    validator.checkEmpty(map.entrySet());
  }

  @Test(groups = "development")
  public void entrySet_addIsSupported() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    assertTrue(map.entrySet().add(new SimpleEntry<Integer, Integer>(1, 2)));
    assertFalse(map.entrySet().add(new SimpleEntry<Integer, Integer>(1, 2)));
    assertEquals(map.entrySet().size(), 1);
    assertEquals(map.size(), 1);
    validator.checkValidState(map);
  }

  @Test(groups = "development")
  public void entrySet_withClear() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    warmUp(map, 0, 100);

    Set<Entry<Integer, Integer>> entries = map.entrySet();
    entries.clear();
    validator.checkEmpty(entries);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void entrySet_whenPopulated() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    warmUp(map, 0, 100);

    assertFalse(entries.contains(new SimpleEntry<Integer, Integer>(0, 200)));
    assertFalse(entries.contains(new Object()));
    assertFalse(entries.remove(new Object()));
    assertEquals(entries.size(), 100);
    for (int i=0; i<100; i++) {
      Entry<Integer, Integer> newEntry = new SimpleEntry<Integer, Integer>(i, -i);
      assertTrue(entries.contains(newEntry));
      assertTrue(entries.remove(newEntry));
      assertFalse(entries.remove(newEntry));
      assertFalse(entries.contains(newEntry));
    }

    validator.checkEmpty(entries);
    validator.checkEmpty(map);
  }

  @Test(groups = "development")
  public void entryIterator() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    Set<Entry<Integer, Integer>> entries = map.entrySet();
    warmUp(map, 0, 100);

    int iterations = 0;
    for (Iterator<Entry<Integer, Integer>> i=map.entrySet().iterator(); i.hasNext();) {
      map.containsValue(i.next());
      i.remove();
      iterations++;
    }
    assertEquals(iterations, 100);
    validator.checkEmpty(map);
  }

  @Test(groups = "development", expectedExceptions = IllegalStateException.class)
  public void entryIterator_noElement() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.entrySet().iterator().remove();
  }

  @Test(groups = "development", expectedExceptions = NoSuchElementException.class)
  public void entryIterator_noMoreElements() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.entrySet().iterator().next();
  }

  @Test(groups = "development")
  public void writeThroughEntry() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();

    map.remove(1);
    assertTrue(map.isEmpty());

    entry.setValue(3);
    assertEquals(1, map.size());
    assertEquals(Integer.valueOf(3), map.get(1));
    validator.checkValidState(map);
  }

  @Test(groups = "development", expectedExceptions = NullPointerException.class)
  public void writeThroughEntry_withNull() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    entry.setValue(null);
  }

  @Test(groups = "development")
  public void writeThroughEntry_serialize() {
    ConcurrentLinkedHashMap<Integer, Integer> map = createGuarded();
    map.put(1, 2);
    Entry<Integer, Integer> entry = map.entrySet().iterator().next();
    Object copy = SerializationUtils.clone((Serializable) entry);
    validator.checkEqualsAndHashCode(entry, copy);
  }
}
