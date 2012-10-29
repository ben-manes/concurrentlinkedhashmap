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

import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static com.googlecode.concurrentlinkedhashmap.IsEmptyCollection.emptyCollection;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * A matcher that evaluates a {@link AbstractLinkedDeque} to determine if it is in a
 * valid state.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsValidLinkedDeque<E>
    extends TypeSafeDiagnosingMatcher<AbstractLinkedDeque<? extends E>> {

  @Override
  public void describeTo(Description description) {
    description.appendText("valid");
  }

  @Override
  protected boolean matchesSafely(AbstractLinkedDeque<? extends E> deque, Description description) {
    DescriptionBuilder builder = new DescriptionBuilder(description);

    if (deque.isEmpty()) {
      checkEmpty(deque, builder);
    }
    checkIterator(deque, deque.iterator(), builder);
    checkIterator(deque, deque.descendingIterator(), builder);

    return builder.matches();
  }

  void checkEmpty(AbstractLinkedDeque<? extends E> deque, DescriptionBuilder builder) {
    builder.expectThat(deque, emptyCollection());
    builder.expectThat(deque.pollFirst(), is(nullValue()));
    builder.expectThat(deque.pollLast(), is(nullValue()));
    builder.expectThat(deque.poll(), is(nullValue()));
  }

  @SuppressWarnings("unchecked")
  void checkIterator(AbstractLinkedDeque<? extends E> deque, Iterator<? extends E> iterator,
      DescriptionBuilder builder) {
    Set<E> seen = Sets.newIdentityHashSet();
    while (iterator.hasNext()) {
      E element = iterator.next();
      checkElement((AbstractLinkedDeque<E>) deque, element, builder);
      String errorMsg = String.format("Loop detected: %s in %s", element, seen);
      builder.expectThat(errorMsg, seen.add(element), is(true));
    }
    builder.expectThat(deque, hasSize(seen.size()));
  }

  void checkElement(AbstractLinkedDeque<E> deque, E element, DescriptionBuilder builder) {
    Object first = deque.peekFirst();
    Object last = deque.peekLast();
    if (element == first) {
      builder.expectThat("not null prev", deque.getPrevious(element), is(nullValue()));
    }
    if (element == last) {
      builder.expectThat("not null next", deque.getNext(element), is(nullValue()));
    }
    if ((element != first) && (element != last)) {
      builder.expectThat(deque.getPrevious(element), is(not(nullValue())));
      builder.expectThat(deque.getNext(element), is(not(nullValue())));
    }
  }

  @Factory
  public static <E> IsValidLinkedDeque<E> validLinkedDeque() {
    return new IsValidLinkedDeque<E>();
  }
}
