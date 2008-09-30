package com.rc.util.concurrent.distribution;

import java.util.concurrent.Callable;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * Creates an exponential distribution.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
final class Exponential implements Callable<Double> {
    private final RandomData random = new RandomDataImpl();
    private final double mean;
    
    /**
     * An exponential distribution.
     * 
     * @param mean The mean value of the distribution.
     */
    public Exponential(double mean) {
        this.mean = mean;
    }
    
    /**
     * Random value with expected mean value.
     */
    public Double call() {
        return random.nextExponential(mean);
    }
}
