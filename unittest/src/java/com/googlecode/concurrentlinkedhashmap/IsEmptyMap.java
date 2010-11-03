package com.googlecode.concurrentlinkedhashmap;

import com.google.common.collect.ImmutableMap;

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

  private boolean isEmpty(ConcurrentLinkedHashMap<?, ?> map, Description description) {
    boolean matches = true;
    map.tryToDrainEvictionQueues(false);
    matches &= check(map.size() == 0, "Size != 0", description);
    matches &= check(map.data.isEmpty(), "Internal not empty", description);
    matches &= check(map.data.size() == 0, "Internal size != 0", description);
    matches &= check(map.weightedSize() == 0, "Weighted size != 0", description);
    matches &= check(map.weightedSize == 0, "Internal weighted size != 0", description);
    matches &= check(map.equals(ImmutableMap.of()), "Not equal to empty map", description);
    matches &= check(map.hashCode() == ImmutableMap.of().hashCode(), "hashcode", description);
    matches &= check(map.toString().equals(ImmutableMap.of().toString()), "toString", description);
    matches &= check(map.sentinel.prev == map.sentinel, "sentinel not linked to prev", description);
    matches &= check(map.sentinel.next == map.sentinel, "sentinel not linked to next", description);
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
