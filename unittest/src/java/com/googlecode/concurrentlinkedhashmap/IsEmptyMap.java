package com.googlecode.concurrentlinkedhashmap;

import com.google.common.collect.ImmutableMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LirsNode;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LirsPolicy;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruNode;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.LruPolicy;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Map;

/**
 * A matcher that performs an exhaustive empty check throughout the {@link Map}
 * and {@link ConcurrentLinkedHashMap} contract.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEmptyMap extends TypeSafeDiagnosingMatcher<Map<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("empty");
  }

  @Override
  protected boolean matchesSafely(Map<?, ?> map, Description description) {
    boolean matches = true;
    matches &= new IsEmptyCollection().matchesSafely(map.keySet(), description);
    matches &= new IsEmptyCollection().matchesSafely(map.values(), description);
    matches &= new IsEmptyCollection().matchesSafely(map.entrySet(), description);
    matches &= check(map.isEmpty(), "Not empty", description);
    matches &= check(map.equals(ImmutableMap.of()), "Not equal to empty map", description);
    matches &= check(map.hashCode() == ImmutableMap.of().hashCode(), "hashcode", description);
    matches &= check(map.toString().equals(ImmutableMap.of().toString()), "toString", description);
    if (map instanceof ConcurrentLinkedHashMap<?, ?>) {
      matches &= isEmpty((ConcurrentLinkedHashMap<?, ?>) map, description);
    }
    return matches;
  }

  @SuppressWarnings("unchecked")
  private boolean isEmpty(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    map.tryToDrainEvictionQueues(false);
    matches &= check(map.size() == 0, "Size != 0", description);
    matches &= check(map.data.isEmpty(), "Internal not empty", description);
    matches &= check(map.data.size() == 0, "Internal size != 0", description);
    matches &= check(map.weightedSize() == 0, "Weighted size != 0", description);
    matches &= check(map.policy.currentSize == 0, "Internal weighted size != 0", description);
    matches &= check(map.equals(ImmutableMap.of()), "Not equal to empty map", description);
    matches &= check(map.hashCode() == ImmutableMap.of().hashCode(), "hashcode", description);
    matches &= check(map.toString().equals(ImmutableMap.of().toString()), "toString", description);
    if (map.policy instanceof LruPolicy) {
      matches &= checkLru(map, description);
    } else if (map.policy instanceof LirsPolicy) {
      matches &= checkLirs(map, description);
    }

    return matches;
  }

  @SuppressWarnings("unchecked")
  private boolean checkLru(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    LruNode sentinel = (LruNode) (Node) map.sentinel;
    matches &= check(sentinel.prev == sentinel, "sentinel not prev", description);
    matches &= check(sentinel.next == sentinel, "sentinel not next", description);
    return matches;
  }

  @SuppressWarnings("unchecked")
  private boolean checkLirs(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    LirsNode sentinel = (LirsNode) (Node) map.sentinel;
    matches &= check(sentinel.prevInStack == sentinel, "sentinel not stack prev", description);
    matches &= check(sentinel.nextInStack == sentinel, "sentinel not stack next", description);
    matches &= check(sentinel.prevInQueue == sentinel, "sentinel not queue prev", description);
    matches &= check(sentinel.nextInQueue == sentinel, "sentinel not queue next", description);
    return matches;
  }

  private boolean check(boolean expression, String errorMsg, Description description) {
    if (!expression) {
      description.appendText(" " + errorMsg);
    }
    return expression;
  }

  @Factory
  public static Matcher<Map<?, ?>> emptyMap() {
    return new IsEmptyMap();
  }
}
