package com.googlecode.concurrentlinkedhashmap.distribution;

import com.google.common.base.Supplier;

/**
 * The distributions to create working sets.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum Distribution {

  Uniform() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double lower = Double.valueOf(System.getProperty("efficiency.distribution.uniform.lower"));
      double upper = Double.valueOf(System.getProperty("efficiency.distribution.uniform.upper"));
      return new Uniform(lower, upper);
    }
  },
  Exponential() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.exponential.mean"));
      return new Exponential(mean);
    }
  },
  Gaussian() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.mean"));
      double sigma = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.sigma"));
      return new Gaussian(mean, sigma);
    }
  },
  Poisson() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.poisson.mean"));
      return new Poisson(mean);
    }
  },
  Zipfian() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double skew = Double.valueOf(System.getProperty("efficiency.distribution.zipfian.skew"));
      return new Zipfian(skew);
    }
  };

  /**
   * Retrieves a new distribution, based on the required system property values.
   */
  public abstract Supplier<Double> getAlgorithm();
}
