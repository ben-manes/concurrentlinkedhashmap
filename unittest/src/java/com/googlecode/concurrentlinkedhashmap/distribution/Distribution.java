package com.googlecode.concurrentlinkedhashmap.distribution;

import java.util.concurrent.Callable;

/**
 * The distributions to create working sets.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public enum Distribution {
    UNIFORM() {
        @Override
        public Callable<Double> getAlgorithm() {
            double lower = Double.valueOf(System.getProperty("efficiency.distribution.uniform.lower"));
            double upper = Double.valueOf(System.getProperty("efficiency.distribution.uniform.upper"));
            return new Uniform(lower, upper);
        }
    },
    EXPONENTIAL() {
        @Override
        public Callable<Double> getAlgorithm() {
            double mean = Double.valueOf(System.getProperty("efficiency.distribution.exponential.mean"));
            return new Exponential(mean);
        }
    },
    GAUSSIAN() {
        @Override
        public Callable<Double> getAlgorithm() {
            double mean = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.mean"));
            double sigma = Double.valueOf(System.getProperty("efficiency.distribution.gaussian.sigma"));
            return new Gaussian(mean, sigma);
        }
    },
    POISSON() {
        @Override
        public Callable<Double> getAlgorithm() {
            double mean = Double.valueOf(System.getProperty("efficiency.distribution.poisson.mean"));
            return new Poisson(mean);
        }
    },
    ZIPFIAN() {
        @Override
        public Callable<Double> getAlgorithm() {
            double skew = Double.valueOf(System.getProperty("efficiency.distribution.zipfian.skew"));
            return new Zipfian(skew);
        }
    };

    /**
     * Retrieves a new distribution, based on the required system property values.
     */
    public abstract Callable<Double> getAlgorithm();
}
