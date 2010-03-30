package com.googlecode.concurrentlinkedhashmap.distribution;

import java.util.concurrent.Callable;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * Creates a Poisson distribution.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
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
