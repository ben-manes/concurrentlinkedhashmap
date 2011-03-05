// Copyright 2011 Google Inc. All Rights Reserved.

package com.googlecode.concurrentlinkedhashmap;

import com.google.caliper.internal.guava.base.Objects;

import org.hamcrest.Description;

/**
 * Assists in implementing {@link org.hamcrest.Matcher}s.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class DescriptionBuilder {
  private final Description description;
  private boolean matches;

  public DescriptionBuilder(Description description) {
    this.description = description;
    this.matches = true;
  }

  public void expectEqual(Object o1, Object o2) {
    expect(Objects.equal(o1, o2));
  }

  public void expectEqual(Object o1, Object o2, String message, Object... args) {
    expect(Objects.equal(o1, o2), message, args);
  }

  public void expectNotEqual(Object o1, Object o2, String message, Object... args) {
    expectNot(Objects.equal(o1, o2), message, args);
  }

  public void expectNot(boolean expression, String message, Object... args) {
    expect(!expression, message, args);
  }

  public void expectNot(boolean expression) {
    expect(!expression);
  }

  public void expect(boolean expression, String message, Object... args) {
    if (!expression) {
      String separator = matches ? "" : ", ";
      description.appendText(separator + String.format(message, args));
    }
    expect(expression);
  }

  public void expect(boolean expression) {
    matches &= expression;
  }

  public Description getDescription() {
    return description;
  }

  public boolean matches() {
    return matches;
  }
}