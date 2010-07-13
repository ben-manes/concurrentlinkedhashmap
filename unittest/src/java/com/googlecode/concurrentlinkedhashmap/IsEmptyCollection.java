package com.googlecode.concurrentlinkedhashmap;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A matcher that performs an exhaustive empty equality check throughout the
 * {@link Collection} contract.
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
    boolean matches = true;
    matches &= check(c.isEmpty(), "not empty", description);
    matches &= check(c.size() == 0, "size = " + c.size(), description);
    matches &= check(!c.iterator().hasNext(), "iterator has data", description);
    matches &= check(c.toArray().length == 0, "toArray has data", description);
    matches &= check(c.toArray(new Object[0]).length == 0, "toArray has data", description);
    if (c instanceof Set<?>) {
      matches &= check(c.hashCode() == emptySet().hashCode(), "hashcode", description);
      matches &= check(c.equals(emptySet()), "collection not equal to empty set", description);
      matches &= check(emptySet().equals(c), "empty set not equal to collection", description);
    } else if (c instanceof List<?>) {
      matches &= check(c.hashCode() == emptyList().hashCode(), "hashcode", description);
      matches &= check(c.equals(emptyList()), "collection not equal to empty list", description);
      matches &= check(emptyList().equals(c), "empty list not equal to collection", description);
    }
    return matches;
  }

  private boolean check(boolean expression, String errorMsg, Description description) {
    if (!expression) {
      description.appendText(" " + errorMsg);
    }
    return expression;
  }

  @Factory
  public static <E> Matcher<Collection<?>> emptyCollection() {
    return new IsEmptyCollection();
  }
}
