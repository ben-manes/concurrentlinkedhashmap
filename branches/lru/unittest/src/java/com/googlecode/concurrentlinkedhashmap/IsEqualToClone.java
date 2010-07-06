package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Maps.newHashMap;
import static com.googlecode.concurrentlinkedhashmap.Validator.checkEmpty;
import static com.googlecode.concurrentlinkedhashmap.Validator.checkValidState;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.io.Serializable;
import java.util.Map;

/**
 * Is a {@link ConcurrentLinkedHashMap} equal to a serialized clone of that
 * value?
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEqualToClone extends BaseMatcher<ConcurrentLinkedHashMap<?, ?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("clone");
  }

  public boolean matches(Object arg) {
    return (arg instanceof ConcurrentLinkedHashMap<?, ?>) && isEqualToClone(cast(arg));
  }

  private ConcurrentLinkedHashMap<?, ?> cast(Object arg) {
    return (ConcurrentLinkedHashMap<?, ?>) arg;
  }

  private boolean isEqualToClone(ConcurrentLinkedHashMap<?, ?> map) {
    Map<?, ?> data = newHashMap(map);

    // TODO(bmanes): replace validations with matchers
    ConcurrentLinkedHashMap<?, ?> copy = clone(map);
    checkValidState(map);
    checkValidState(copy);
    if (data.isEmpty()) {
      checkEmpty(map);
      checkEmpty(copy);
    }
    return new EqualsBuilder()
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

  /** Is the value equal to a a cloned instance? */
  @Factory
  public static Matcher<ConcurrentLinkedHashMap<?, ?>> isEqualToClone() {
    return new IsEqualToClone();
  }
}
