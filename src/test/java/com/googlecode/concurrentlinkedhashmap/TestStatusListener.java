// Copyright 2011 Google Inc. All Rights Reserved.
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
