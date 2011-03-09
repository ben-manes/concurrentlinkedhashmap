/*
 * Copyright 2011 Benjamin Manes
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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * A matcher that performs an exhaustive empty check throughout the
 * {@link Collection}, {@link Set}, {@link List}, {@link Queue}, and
 * {@link Deque} contracts.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEmptyCollection extends TypeSafeDiagnosingMatcher<Collection<?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("empty");
  }

  @Override
  protected boolean matchesSafely(Collection<?> c, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    checkCollection(c, builder);
    if (c instanceof Set<?>) {
      checkSet((Set<?>) c, builder);
    }
    if (c instanceof List<?>) {
      checkList((List<?>) c, builder);
    }
    if (c instanceof Queue<?>) {
      checkQueue((Queue<?>) c, builder);
    }
    if (c instanceof Deque<?>) {
      checkDeque((Deque<?>) c, builder);
    }
    return builder.matches();
  }

  private void checkCollection(Collection<?> c, DescriptionBuilder builder) {
    builder.expect(c.isEmpty(), "not empty");
    builder.expectEqual(c.size(), 0, "size = " + c.size());
    builder.expect(!c.iterator().hasNext(), "iterator has data");
    builder.expectEqual(c.toArray().length, 0, "toArray has data");
    builder.expectEqual(c.toArray(new Object[0]).length, 0, "toArray has data");
  }

  private void checkSet(Set<?> set, DescriptionBuilder builder) {
    builder.expectEqual(set.hashCode(), emptySet().hashCode(), "hashcode");
    builder.expectEqual(set, emptySet(), "collection not equal to empty set");
    builder.expectEqual(emptySet(), set, "empty set not equal to collection");
  }

  private void checkList(List<?> list, DescriptionBuilder builder) {
    builder.expectEqual(list.hashCode(), emptyList().hashCode(), "hashcode");
    builder.expectEqual(list, emptyList(), "collection not equal to empty list");
    builder.expectEqual(emptyList(), list, "empty list not equal to collection");
  }

  private void checkQueue(Queue<?> queue, DescriptionBuilder builder) {
    builder.expectEqual(queue.peek(), null);
    builder.expectEqual(queue.poll(), null);
  }

  private void checkDeque(Deque<?> deque, DescriptionBuilder builder) {
    builder.expectEqual(deque.peekFirst(), null);
    builder.expectEqual(deque.peekLast(), null);
    builder.expectEqual(deque.pollFirst(), null);
    builder.expectEqual(deque.pollLast(), null);
    builder.expect(!deque.descendingIterator().hasNext());
  }

  @Factory
  public static <E> Matcher<Collection<?>> emptyCollection() {
    return new IsEmptyCollection();
  }
}
