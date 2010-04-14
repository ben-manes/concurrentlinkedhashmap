package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.Test;

import static java.lang.String.format;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A unit-test for {@link java.util.concurrent.ConcurrentMap} interface and its
 * serializability. These tests do not assert correct concurrency behavior.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@SuppressWarnings("unchecked")
public final class ConcurrentMapTest extends BaseTest {

  @Test(groups = "development")
  public void empty() {
    debug(" * empty: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
    validator.state(cache);
    validator.empty(cache);
  }

  @Test(groups = "development")
  public void putAll() {
    debug(" * putAll: START");
    ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap();
    ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
    cache.putAll(expected);

    validator.state(cache);
    assertEquals(cache, expected);
  }

  @Test(groups = "development")
  public void put() {
    debug(" * put: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = create(capacity);
    cache.put(0, 0);
    int old = cache.put(0, 1);
    int current = cache.get(0);

    assertEquals(old, 0);
    assertEquals(current, 1);

    validator.state(cache);
  }

  @Test(groups = "development")
  public void putIfAbsent() {
    debug(" * putIfAbsent: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
    for (Integer i = 0; i < capacity; i++) {
      assertNull(cache.putIfAbsent(i, i));
      assertEquals(cache.putIfAbsent(i, -1), i);
      assertEquals(cache.data.get(i).weightedValue.value, i);
    }
    assertEquals(cache.size(), capacity, "Not warmed to max size");
    validator.state(cache);
    assertEquals(cache, createWarmedMap());
  }

  @Test(groups = "development")
  public void retrieval() {
    debug(" * retrieval: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
    for (Integer i = -capacity; i < 0; i++) {
      assertNull(cache.get(i));
      assertFalse(cache.containsKey(i));
      assertFalse(cache.containsValue(i));
    }
    for (Integer i = 0; i < capacity; i++) {
      assertEquals(cache.get(i), i);
      assertTrue(cache.containsKey(i));
      assertTrue(cache.containsValue(i));
    }
    for (Integer i = capacity; i < capacity * 2; i++) {
      assertNull(cache.get(i));
      assertFalse(cache.containsKey(i));
      assertFalse(cache.containsValue(i));
    }
    validator.state(cache);
  }

  @Test(groups = "development")
  public void remove() {
    debug(" * remove: START");
    EvictionMonitor guard = EvictionMonitor.newGuard();

    // Map#remove()
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
    for (Integer i = 0; i < capacity; i++) {
      assertEquals(cache.remove(i), i, format("Failure on index #%d", i));
      assertNull(cache.remove(i), "Not fully removed");
      assertFalse(cache.containsKey(i));
    }
    validator.state(cache);
    validator.empty(cache);

    // ConcurrentMap#remove()
    cache = createWarmedMap(guard);
    for (Integer i = 0; i < capacity; i++) {
      assertFalse(cache.remove(i, -1));
      assertTrue(cache.remove(i, i));
      assertFalse(cache.remove(i, -1));
      assertFalse(cache.containsKey(i));
    }
    validator.state(cache);
    validator.empty(cache);
  }

  @Test(groups = "development")
  public void replace() {
    debug(" * replace: START");
    Integer dummy = -1;
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
    for (Integer i = 0; i < capacity; i++) {
      assertNotNull(cache.replace(i, dummy));
      assertFalse(cache.replace(i, i, i));
      assertEquals(cache.data.get(i).weightedValue.value, dummy);
      assertTrue(cache.replace(i, dummy, i));
      assertEquals(cache.remove(i), i);
      assertNull(cache.replace(i, i));
    }
    validator.state(cache);
    validator.empty(cache);
  }

  @Test(groups = "development")
  public void clear() {
    debug(" * clear: START");
    EvictionMonitor guard = EvictionMonitor.newGuard();
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
    cache.clear();
    validator.state(cache);
  }

  @Test(groups = "development")
  public void keySet() {
    debug(" * keySet: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
    Set<Integer> keys = cache.keySet();
    Integer key = keys.iterator().next();
    int initialSize = cache.size();

    // key set reflects map
    assertEquals(keys, cache.data.keySet());
    try {
      assertFalse(keys.add(key));
      fail("Not supported by maps");
    } catch (UnsupportedOperationException e) {
      // expected
    }

    assertTrue(keys.contains(key));
    assertTrue(keys.remove(key));
    assertFalse(keys.remove(key));
    assertFalse(keys.contains(key));
    assertEquals(keys.size(), initialSize - 1);
    assertEquals(cache.size(), initialSize - 1);

    // key iterator
    Iterator<Integer> iterator = keys.iterator();
    for (Node node : cache.data.values()) {
      assertEquals(iterator.next(), node.key);
    }
    assertFalse(iterator.hasNext());

    Iterator<Integer> iter = keys.iterator();
    Integer i = iter.next();
    iter.remove();
    assertFalse(keys.contains(i));
    assertFalse(cache.containsKey(i));
    assertEquals(keys.size(), initialSize - 2);
    assertEquals(cache.size(), initialSize - 2);

    // toArray
    assertTrue(Arrays.equals(keys.toArray(), cache.data.keySet().toArray()));
    assertTrue(Arrays.equals(keys.toArray(new Integer[cache.size()]),
                             cache.data.keySet().toArray(new Integer[cache.size()])));

    // other
    cache.setCapacity(capacity / 2);
    assertEquals(keys.size(), capacity / 2);

    keys.clear();
    assertTrue(cache.isEmpty());
    assertTrue(keys.isEmpty());
  }

  @Test(groups = "development")
  public void values() {
    debug(" * values: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
    Collection<Integer> values = cache.values();
    Integer value = values.iterator().next();
    int initialSize = cache.size();

    // value collection reflects map
    try {
      assertFalse(values.add(value));
      fail("Not supported by maps");
    } catch (UnsupportedOperationException e) {
      // expected
    }

    assertTrue(values.contains(value));
    assertTrue(values.remove(value));
    assertFalse(values.remove(value));
    assertFalse(values.contains(value));
    assertEquals(values.size(), initialSize - 1);
    assertEquals(cache.size(), initialSize - 1);

    // values iterator
    Iterator<Integer> iterator = values.iterator();
    for (Node node : cache.data.values()) {
      assertEquals(iterator.next(), node.weightedValue.value);
    }
    assertFalse(iterator.hasNext());

    Iterator<Integer> iter = values.iterator();
    Integer i = iter.next();
    iter.remove();
    assertFalse(values.contains(i));
    assertFalse(cache.containsValue(i));
    assertEquals(values.size(), initialSize - 2);
    assertEquals(cache.size(), initialSize - 2);

    // toArray
    List<Integer> list = new ArrayList<Integer>();
    for (Node node : cache.data.values()) {
      list.add((Integer) node.weightedValue.value);
    }
    assertTrue(Arrays.equals(values.toArray(), list.toArray()));
    assertTrue(Arrays.equals(values.toArray(new Integer[cache.size()]),
                             list.toArray(new Integer[cache.size()])));

    // other
    cache.setCapacity(capacity / 2);
    assertEquals(values.size(), capacity / 2);

    values.clear();
    assertTrue(cache.isEmpty());
    assertTrue(values.isEmpty());
  }

  @Test(groups = "development")
  public void entrySet() {
    debug(" * entrySet: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
    Set<Entry<Integer, Integer>> entries = cache.entrySet();
    Entry<Integer, Integer> entry = entries.iterator().next();
    int initialSize = cache.size();

    // Entry updates reflect map
    Integer oldValue = entry.getValue();
    assertEquals(entry.setValue(Integer.MIN_VALUE), oldValue);
    assertTrue(entry.getValue().equals(Integer.MIN_VALUE));
    assertTrue(cache.containsValue(Integer.MIN_VALUE));

    // entryset reflects map
    assertEquals(entries.size(), initialSize);
    assertTrue(entries.contains(entry));
    assertFalse(entries.add(entry));
    assertTrue(entries.remove(entry));
    assertFalse(entries.remove(entry));
    assertFalse(cache.containsKey(entry.getKey()));
    assertFalse(cache.containsValue(entry.getValue()));
    assertEquals(entries.size(), initialSize - 1);
    assertEquals(cache.size(), initialSize - 1);
    assertTrue(entries.add(entry));
    assertEquals(entries.size(), initialSize);

    // entry iterator
    Map<Integer, Integer> map = new HashMap<Integer, Integer>();
    for (Entry<Integer, Integer> e : entries) {
      map.put(e.getKey(), e.getValue());
    }
    assertEquals(cache, map);

    Iterator<Entry<Integer, Integer>> iter = entries.iterator();
    Entry<Integer, Integer> e = iter.next();
    iter.remove();
    assertFalse(entries.contains(e));
    assertFalse(cache.containsKey(e.getKey()));
    assertEquals(entries.size(), initialSize - 1);
    assertEquals(cache.size(), initialSize - 1);

    // toArray
    List<Entry<Integer, Integer>> list =
        asList(entries.toArray((Entry<Integer, Integer>[]) new Entry[initialSize - 1]));
    assertTrue(new HashSet<Entry<Integer, Integer>>(list).equals(entries));
    assertTrue(new HashSet(asList(entries.toArray())).equals(entries));

    // other
    cache.setCapacity(capacity / 2);
    assertEquals(entries.size(), capacity / 2);

    entries.clear();
    assertTrue(cache.isEmpty());
    assertTrue(entries.isEmpty());
  }

  @Test(groups = "development")
  public void object() {
    debug(" * object: START");
    ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
    Map<Integer, Integer> expected = new ConcurrentHashMap<Integer, Integer>(capacity);
    for (Integer i = 0; i < capacity; i++) {
      expected.put(i, i);
    }
    assertEquals(cache, expected);
    assertEquals(cache.hashCode(), expected.hashCode());
  }

  /**
   * Tests serialization.
   */
  @Test(groups="development")
  public void serialize() {
    debug(" * serialize: START");

    // default listener & weigher, custom concurrencyLevel
    ConcurrentLinkedHashMap<Integer, Integer> expected = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity)
        .concurrencyLevel(32)
        .build();
    warm(expected, capacity);

    ConcurrentLinkedHashMap actual = (ConcurrentLinkedHashMap) SerializationUtils.clone(expected);
    assertEquals(actual, expected);
    assertEquals(actual.concurrencyLevel, 32);
    assertEquals(actual.maximumWeightedSize, expected.maximumWeightedSize);
    assertEquals(actual.listener, expected.listener);
    assertEquals(actual.weigher, expected.weigher);
    validator.state((ConcurrentLinkedHashMap<Integer, Integer>) actual);

    // custom listener & weigher
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> expected2 =
        new Builder<Integer, Collection<Integer>>()
            .listener(EvictionMonitor.<Integer, Collection<Integer>>newGuard())
            .weigher(Weighers.<Integer>collection())
            .maximumWeightedCapacity(capacity)
            .build();
    actual = (ConcurrentLinkedHashMap) SerializationUtils.clone(expected2);
    assertEquals(actual.listener.getClass(), expected2.listener.getClass());
    assertEquals(actual.weigher, expected2.weigher);
  }
}
