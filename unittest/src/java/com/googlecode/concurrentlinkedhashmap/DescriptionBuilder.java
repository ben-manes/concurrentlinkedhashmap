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

import com.google.common.base.Objects;

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