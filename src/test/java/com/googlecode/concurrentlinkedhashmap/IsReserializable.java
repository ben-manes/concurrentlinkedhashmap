/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import java.io.Serializable;
import java.util.Map;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.BoundedEntryWeigher;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static com.google.common.collect.Maps.newHashMap;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyMap.emptyMap;
import static com.googlecode.concurrentlinkedhashmap.IsValidConcurrentLinkedHashMap.valid;

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

  private boolean matchesSafely(ConcurrentLinkedHashMap<?, ?> original,
      ConcurrentLinkedHashMap<?, ?> copy, EqualsBuilder builder) {
    if (original.weigher instanceof BoundedEntryWeigher<?, ?>) {
      builder.append(((BoundedEntryWeigher<?, ?>) original.weigher).weigher.getClass(),
          ((BoundedEntryWeigher<?, ?>) copy.weigher).weigher.getClass());
    }
    Map<?, ?> data = newHashMap(original);
    return builder
        .append(valid().matches(original), true)
        .append(valid().matches(copy), true)
        .append(data.isEmpty(), emptyMap().matches(original))
        .append(data.isEmpty(), emptyMap().matches(copy))
        .append(original.weigher.getClass(), copy.weigher.getClass())
        .append(original.listener.getClass(), copy.listener.getClass())
        .append(original.concurrencyLevel, copy.concurrencyLevel)
        .append(original.hashCode(), copy.hashCode())
        .append(original.capacity(), copy.capacity())
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
