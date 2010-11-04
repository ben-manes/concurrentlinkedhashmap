package com.googlecode.concurrentlinkedhashmap.distribution;

import com.google.common.base.Supplier;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * Creates an exponential distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Exponential implements Supplier<Double> {
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
  public Double get() {
    return random.nextExponential(mean);
  }
}
