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

import com.google.common.collect.Sets;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

/**
 * A matcher that evaluates a {@link LinkedDeque} to determine if it is in a
 * valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsValidLinkedDeque
    extends TypeSafeDiagnosingMatcher<LinkedDeque<?>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("deque");
  }

  @Override
  protected boolean matchesSafely(LinkedDeque<?> deque, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    if (deque.isEmpty()) {
      checkEmpty(deque, builder);
    }
    checkIterator(deque, deque.iterator(), builder);
    checkIterator(deque, deque.descendingIterator(), builder);

    return builder.matches();
  }

  void checkEmpty(Deque<? extends Linked<?>> deque, DescriptionBuilder builder) {
    IsEmptyCollection.emptyCollection().matchesSafely(deque, builder.getDescription());
    builder.expectEqual(deque.poll(), null);
    builder.expectEqual(deque.pollFirst(), null);
    builder.expectEqual(deque.pollLast(), null);
  }

  void checkIterator(Deque<? extends Linked<?>> deque, Iterator<? extends Linked<?>> iterator,
      DescriptionBuilder builder) {
    Set<Linked<?>> seen = Sets.newIdentityHashSet();
    while (iterator.hasNext()) {
      Linked<?> element = iterator.next();
      checkElement(deque, element, builder);
      builder.expect(seen.add(element), "Loop detected: %s in %s", element, seen);
    }
    builder.expectEqual(deque.size(), seen.size());
  }

  void checkElement(Deque<? extends Linked<?>> deque, Linked<?> element,
      DescriptionBuilder builder) {
    Linked<?> first = deque.peekFirst();
    Linked<?> last = deque.peekLast();
    if (element == first) {
      builder.expectEqual(element.getPrevious(), null, "not null prev");
    }
    if (element == last) {
      builder.expectEqual(element.getNext(), null, "not null next");
    }
    if ((element != first) && (element != last)) {
      builder.expectNotEqual(element.getPrevious(), null, "null prev");
      builder.expectNotEqual(element.getNext(), null, "null next");
    }
  }

  @Factory
  public static IsValidLinkedDeque validLinkedDeque() {
    return new IsValidLinkedDeque();
  }
}
