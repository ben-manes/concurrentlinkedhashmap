package com.reardencommerce.kernel.collections.shared.evictable.distribution;

import java.util.Date;
import java.util.concurrent.Callable;

import cern.jet.random.Distributions;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
 * Creates a Zipfian distribution.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
final class Zipfian implements Callable<Double> {
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
    public Double call() {
        return (double) Distributions.nextZipfInt(skew, random);
    }
}
