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
 * A matcher that performs an exhaustive empty check throughout the
 * {@link Collection}, {@link Set}, and {@link List} contracts.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class IsEmptyCollection extends TypeSafeDiagnosingMatcher<Collection<?>> {

  public void describeTo(Description description) {
    description.appendText("empty");
  }

  @Override
  protected boolean matchesSafely(Collection<?> c, Description description) {
    boolean matches = checkCollection(c, description);
    if (c instanceof Set<?>) {
      matches &= checkSet((Set<?>) c, description);
    } else if (c instanceof List<?>) {
      matches &= checkList((List<?>) c, description);
    }
    return matches;
  }

  private boolean checkCollection(Collection<?> c, Description description) {
    boolean matches = true;
    matches &= check(c.isEmpty(), "not empty", description);
    matches &= check(c.size() == 0, "size = " + c.size(), description);
    matches &= check(!c.iterator().hasNext(), "iterator has data", description);
    matches &= check(c.toArray().length == 0, "toArray has data", description);
    matches &= check(c.toArray(new Object[0]).length == 0, "toArray has data", description);
    return matches;
  }

  private boolean checkSet(Set<?> set, Description description) {
    boolean matches = true;
    matches &= check(set.hashCode() == emptySet().hashCode(), "hashcode", description);
    matches &= check(set.equals(emptySet()), "collection not equal to empty set", description);
    matches &= check(emptySet().equals(set), "empty set not equal to collection", description);
    return matches;
  }

  private boolean checkList(List<?> list, Description description) {
    boolean matches = true;
    matches &= check(list.hashCode() == emptyList().hashCode(), "hashcode", description);
    matches &= check(list.equals(emptyList()), "collection not equal to empty list", description);
    matches &= check(emptyList().equals(list), "empty list not equal to collection", description);
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
