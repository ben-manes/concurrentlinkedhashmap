package com.googlecode.concurrentlinkedhashmap.distribution;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

import java.util.concurrent.Callable;

/**
 * Creates an exponential distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Exponential implements Callable<Double> {
  private final RandomData random = new RandomDataImpl();
  private final double mean;

  /**
   * An exponential distribution.
   *
   * @param mean The mean value of the distribution.
   */
  public Exponential(double mean) {
    this.mean = mean;
  }

  /**
   * Random value with expected mean value.
   */
  public Double call() {
    return random.nextExponential(mean);
  }
}
