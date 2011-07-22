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

import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

/**
 * A listener that pretty-prints the status of the tests.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class TestStatusListener extends TestListenerAdapter {
  private boolean executionStart = true;
  private boolean showSuite;
  private String testSuite;
  private String testClass;
  private int testCount;

  @Override
  public void onStart(ITestContext context) {
    if (executionStart) {
      System.out.println();
      executionStart = false;
    }
    testSuite = context.getName();
    showSuite = true;
  }

  @Override
  public void onFinish(ITestContext context) {
    if (!showSuite) {
      System.out.println("\n");
    }
  }

  @Override
  public void onTestStart(ITestResult result) {
    if (showSuite) {
      System.out.printf("[%s]", testSuite);
      showSuite = false;
    }

    String name = result.getTestClass().getRealClass().getSimpleName();
    if (!name.equals(testClass)) {
      testCount = 0;
      testClass = name;
      System.out.printf("\nRunning %s\n", testClass);
    }
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    log(".");
  }

  @Override
  public void onTestFailure(ITestResult result) {
    log("F");
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    log("S");
  }

  private void log(String string) {
    System.out.print(string);
    if (++testCount % 40 == 0) {
      System.out.println("");
    }
  }
}
