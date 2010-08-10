package com.googlecode.concurrentlinkedhashmap.distribution;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

import java.util.concurrent.Callable;

/**
 * Creates a Poisson distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Poisson implements Callable<Double> {
  private final RandomData random = new RandomDataImpl();
  private final double mean;

  /**
   * A Poisson distribution.
   *
   * @param mean The mean value of the distribution.
   */
  public Poisson(double mean) {
    this.mean = mean;
  }

  /**
   * Random value with expected mean value.
   */
  public Double call() {
    return (double) random.nextPoisson(mean);
  }
}
