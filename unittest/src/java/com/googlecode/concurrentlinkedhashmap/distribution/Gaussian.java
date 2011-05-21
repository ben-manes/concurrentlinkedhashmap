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
  public Double get() {
    return random.nextGaussian(mean, sigma);
  }
}
