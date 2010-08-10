package com.googlecode.concurrentlinkedhashmap.distribution;

import com.google.common.base.Supplier;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * Creates a Gaussian distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Gaussian implements Supplier<Double> {
  private final RandomData random = new RandomDataImpl();
  private final double sigma;
  private final double mean;

  /**
   * A Gaussian distribution.
   *
   * @param mean  The mean value of the distribution.
   * @param sigma The standard deviation of the distribution.
   */
  public Gaussian(double mean, double sigma) {
    this.mean = mean;
    this.sigma = sigma;
  }

  /**
   * Random value with the given mean and standard deviation.
   */
  @Override
  public Double get() {
    return random.nextGaussian(mean, sigma);
  }
}
