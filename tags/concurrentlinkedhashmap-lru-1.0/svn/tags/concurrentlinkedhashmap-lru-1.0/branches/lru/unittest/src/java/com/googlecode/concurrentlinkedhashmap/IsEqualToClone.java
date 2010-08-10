package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Maps.newHashMap;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyMap.emptyMap;
import static com.googlecode.concurrentlinkedhashmap.IsValidState.valid;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.io.Serializable;
import java.util.Map;

/**
 * A matcher that evaluates a {@link ConcurrentLinkedHashMap} by creating a
 * serialized clone and checking its equality.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEqualToClone extends TypeSafeMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendValue("clone");
  }

  @Override
  public boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> map) {
    Map<?, ?> data = newHashMap(map);
    ConcurrentLinkedHashMap<?, ?> copy = clone(map);
    return new EqualsBuilder()
        .append(valid().matches(map), true)
        .append(valid().matches(copy), true)
        .append(emptyMap().matches(map), data.isEmpty())
        .append(emptyMap().matches(copy), data.isEmpty())
        .append(copy.maximumWeightedSize, map.maximumWeightedSize)
        .append(copy.listener.getClass(), map.listener.getClass())
        .append(copy.weigher.getClass(), map.weigher.getClass())
        .append(copy.concurrencyLevel, map.concurrencyLevel)
        .append(copy.hashCode(), map.hashCode())
        .append(copy, map)
        .append(map, copy)
        .append(data, map)
        .isEquals();
  }

  @SuppressWarnings("unchecked")
  private <T extends Serializable> T clone(T object) {
    return (T) SerializationUtils.clone(object);
  }

  @Factory
  public static <K, V> Matcher<ConcurrentLinkedHashMap<?, ?>> equalToClone() {
    return new IsEqualToClone();
  }
}
