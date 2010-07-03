package com.googlecode.concurrentlinkedhashmap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.Serializable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Base utilities for testing purposes.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public abstract class BaseTest extends Assert {
  protected Validator validator;
  private boolean debug;

  /** Retrieves the maximum weighted capacity to build maps with. */
  protected abstract int capacity();

  /** Initializes the test with runtime properties. */
  @BeforeClass(alwaysRun = true)
  public void before() {
    validator = new Validator(booleanProperty("test.exhaustive"));
    debug = booleanProperty("test.debugMode");
    info("\nRunning %s...\n", getClass().getSimpleName());
  }

  protected static int intProperty(String property) {
    return Integer.valueOf(System.getProperty(property));
  }

  protected static boolean booleanProperty(String property) {
    return Boolean.valueOf(System.getProperty(property));
  }

  /* ---------------- Logging methods -------------- */

  protected void info(String message, Object... args) {
    System.out.printf(message + "\n", args);
  }

  protected void debug(String message, Object... args) {
    if (debug) {
      info(message, args);
    }
  }

  /* ---------------- Testing aspects -------------- */

  private ConcurrentLinkedHashMap<?, ?> rawMap;

  /** Provides a guarded map for test methods. */
  @DataProvider(name = "guarded")
  public Object[][] providesGuardedMap() {
    rawMap = newGuarded();
    return new Object[][] {{ rawMap }};
  }

  /** Creates a map that fails if an eviction occurs. */
  protected <K, V> ConcurrentLinkedHashMap<K, V> newGuarded() {
    return new Builder<K, V>()
        .listener(new GuardingListener<K, V>())
        .maximumWeightedCapacity(capacity())
        .build();
  }

  /** Provides a warmed map for test methods. */
  @DataProvider(name = "warmed")
  public Object[][] providesWarmedMap() {
    rawMap = newWarmedMap();
    return new Object[][] {{ rawMap }};
  }

  protected ConcurrentLinkedHashMap<Integer, Integer> newWarmedMap() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .build();
    warmUp(map, 0, capacity());
    return map;
  }

  protected void warmUp(Map<Integer, Integer> map, int start, int end) {
    for (Integer i = start; i < end; i++) {
      assertNull(map.put(i, -i));
    }
  }

  /** Validates the state of a provided map. */
  @AfterMethod(inheritGroups = true)
  public void verifyValidState(ITestResult result) {
    if (rawMap == null) { // dataProvider not used
      return;
    }
    try {
      validator.checkValidState(rawMap);
    } catch (Throwable caught) {
      fail("Test: " + result.getMethod().getMethodName(), caught);
    }
  }

  /* ---------------- Listener support -------------- */

  private EvictionListener<?, ?> rawListener;

  /** Provides a listener that fails on eviction. */
  @DataProvider(name = "guardingListener")
  public Object[][] providesGuardedListener() {
    rawListener = new GuardingListener<Object, Object>();
    return new Object[][] {{ rawListener }};
  }

  /** Provides a listener that collects evicted entries. */
  @DataProvider(name = "collectingListener")
  public Object[][] providesMonitorableListener() {
    rawListener = new CollectingListener<Object, Object>();
    return new Object[][] {{ rawListener }};
  }

  /** A listener that fails if invoked. */
  private static final class GuardingListener<K, V>
    implements EvictionListener<K, V>, Serializable {

    @Override public void onEviction(Object key, Object value) {
      fail(String.format("Evicted %s=%s", key, value));
    }

    private static final long serialVersionUID = 1L;
  }

  /** A listener that either collects the evicted entries. */
  protected static final class CollectingListener<K, V>
      implements EvictionListener<K, V>, Serializable {
    final Collection<Entry<K, V>> evicted;

    private CollectingListener() {
      this.evicted = new ConcurrentLinkedQueue<Entry<K, V>>();
    }

    @Override public void onEviction(K key, V value) {
      evicted.add(new SimpleImmutableEntry<K, V>(key, value));
    }

    private static final long serialVersionUID = 1L;
  }

  /* ---------------- Node List support -------------- */

  protected static String listForwardToString(ConcurrentLinkedHashMap<?, ?> map) {
    return listToString(map, true);
  }

  protected static String listBackwardsToString(ConcurrentLinkedHashMap<?, ?> map) {
    return listToString(map, false);
  }

  @SuppressWarnings("unchecked")
  private static String listToString(ConcurrentLinkedHashMap<?, ?> map, boolean forward) {
    map.evictionLock.lock();
    try {
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        StringBuilder buffer = new StringBuilder("\n");
        Node current = forward ? map.sentinel.next : map.sentinel.prev;
        while (current != map.sentinel) {
          buffer.append(nodeToString(current)).append("\n");
          if (seen.add(current)) {
            buffer.append("Failure: Loop detected\n");
            break;
          }
          current = forward ? current.next : current.next.prev;
        }
        return buffer.toString();
    } finally {
      map.evictionLock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static String nodeToString(Node node) {
    if (node == null) {
      return "null";
    } else if (node.segment == -1) {
      return "setinel";
    }
    return node.key + "=" + node.weightedValue.value;
  }

  /** Finds the node in the map by walking the list. Returns null if not found. */
  @SuppressWarnings("unchecked")
  protected static Node findNode(Object key, ConcurrentLinkedHashMap<?, ?> map) {
    map.evictionLock.lock();
    try {
      Node current = map.sentinel;
      while (current != map.sentinel) {
        if (current.equals(key)) {
          return current;
        }
      }
      return null;
    } finally {
      map.evictionLock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static int listLength(ConcurrentLinkedHashMap<?, ?> map) {
    map.evictionLock.lock();
    try {
      int length = 0;
      Node current = map.sentinel;
      while (current != map.sentinel) {
        length++;
      }
      return length;
    } finally {
      map.evictionLock.unlock();
    }
  }
}
