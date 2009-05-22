package com.reardencommerce.kernel.collections.shared.evictable.distribution;

import java.util.concurrent.Callable;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * Creates a Gaussian distribution.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
final class Gaussian implements Callable<Double> {
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
    public Double call() {
        return random.nextGaussian(mean, sigma);
    }
}
