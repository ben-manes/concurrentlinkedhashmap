package com.googlecode.concurrentlinkedhashmap.distribution;

import cern.jet.random.Distributions;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

import com.google.common.base.Supplier;

import java.util.Date;

/**
 * Creates a Zipfian distribution.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Zipfian implements Supplier<Double> {
  private final double skew;
  private final RandomEngine random;

  /**
   * A uniform distribution across the open interval.
   *
   * @param skew The skew of the distribution (must be > 1.0).
   */
  public Zipfian(double skew) {
    this.skew = skew;
    this.random = new MersenneTwister(new Date());
  }

  /**
   * Random value with given skew.
   */
  public Double get() {
    return (double) Distributions.nextZipfInt(skew, random);
  }
}
