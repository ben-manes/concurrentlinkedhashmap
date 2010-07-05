package com.googlecode.concurrentlinkedhashmap.distribution;

import com.google.common.base.Supplier;

/**
 * The distributions to create working sets.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public enum Distribution {

  UNIFORM() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double lower = Double.valueOf(System.getProperty("efficiency.distribution.uniform.lower"));
      double upper = Double.valueOf(System.getProperty("efficiency.distribution.uniform.upper"));
      return new Uniform(lower, upper);
    }
  },
  EXPONENTIAL() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.exponential.mean"));
      return new Exponential(mean);
    }
  },
  GAUSSIAN() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.mean"));
      double sigma = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.sigma"));
      return new Gaussian(mean, sigma);
    }
  },
  POISSON() {
    @Override
    public Supplier<Double> getAlgorithm() {
      double mean = Double.valueOf(System.getProperty("efficiency.distribution.poisson.mean"));
      return new Poisson(mean);
    }
  },
  ZIPFIAN() {
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
