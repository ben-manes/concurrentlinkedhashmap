/*
 * Copyright 2011 Benjamin Manes
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
package com.googlecode.concurrentlinkedhashmap.distribution;

import com.google.common.base.Supplier;

import cern.jet.random.Distributions;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

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
