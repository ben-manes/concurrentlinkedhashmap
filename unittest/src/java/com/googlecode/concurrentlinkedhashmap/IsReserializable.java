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
 * A matcher that evaluates an object by creating a serialized copy and checking
 * its equality. In addition to basic equality, this matcher has first class
 * support for exhaustively checking a {@link ConcurrentLinkedHashMap}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsReserializable<T> extends TypeSafeMatcher<T> {

  @Override
  public void describeTo(Description description) {
    description.appendValue("serialized clone");
  }

  @Override
  public boolean matchesSafely(T item) {
    T copy = reserialize(item);

    EqualsBuilder builder = new EqualsBuilder()
        .append(item.hashCode(), copy.hashCode())
        .append(item, copy)
        .append(copy, item);
    if (item instanceof ConcurrentLinkedHashMap<?, ?>) {
      return matchesSafely((ConcurrentLinkedHashMap<?, ?>) item,
          (ConcurrentLinkedHashMap<?, ?>) copy, builder);
    }
    return builder.isEquals();
  }

  private boolean matchesSafely(
      ConcurrentLinkedHashMap<?, ?> original,
      ConcurrentLinkedHashMap<?, ?> copy,
      EqualsBuilder builder) {
    Map<?, ?> data = newHashMap(original);
    return new EqualsBuilder()
        .append(valid().matches(original), true)
        .append(valid().matches(copy), true)
        .append(data.isEmpty(), emptyMap().matches(original))
        .append(data.isEmpty(), emptyMap().matches(copy))
        .append(original.maximumWeightedSize, copy.maximumWeightedSize)
        .append(original.listener.getClass(), copy.listener.getClass())
        .append(original.weigher.getClass(), copy.weigher.getClass())
        .append(original.concurrencyLevel, copy.concurrencyLevel)
        .append(original.hashCode(), copy.hashCode())
        .append(original, data)
        .isEquals();
  }

  @SuppressWarnings("unchecked")
  private T reserialize(T object) {
    return (T) SerializationUtils.clone((Serializable) object);
  }

  @Factory
  public static <T> Matcher<T> reserializable() {
    return new IsReserializable<T>();
  }
}
