package com.rc.util.concurrent.performance;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.random.RandomData;
import org.apache.commons.math.random.RandomDataImpl;

/**
 * A helper class to determine the efficiency of caches.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class CacheEfficiencyTestHarness {
    public static enum Distribution {
        UNIFORM("Random value from the open interval (endpoints included) [arguments: lower, upper]"),
        EXPONENTIAL("Random value from the exponential distribution with expected value = mean [arguments: mean]"),
        GAUSSIAN("Random value with the given mean and standard deviation [arguments: mean, sigma]"),
        POISSON("Random value with the given mean [arguments: mean]");
        
        private final String help;
        private static final RandomData random = new RandomDataImpl();
        private Distribution(String help) {
            this.help = help;
        }
        public double next(double... args) {
            switch (this) {
                case UNIFORM: 
                    return random.nextUniform(args[0], args[1]);
                case EXPONENTIAL:
                    return random.nextExponential(args[0]);
                case GAUSSIAN:
                    return random.nextGaussian(args[0], args[1]);
                case POISSON:
                    return random.nextPoisson(args[0]);
                default:
                    throw new IllegalStateException();
            }
        }
        public String toHelp() {
            return toString() + ": " + help;
        }
    }
    
    /**
     * Creates a random working set based on an exponential distribution.
     * 
     * @param mean The mean value of the distribution.
     * @param size The size of the working set.
     * @return     A random working set.
     */
    public static List<Integer> createWorkingSet(Distribution distribution, int size, double... args) {
        List<Integer> workingSet = new ArrayList<Integer>(size);
        for (int i=0; i<size; i++) {
            workingSet.add((int) Math.round(distribution.next(args)));
        }
        return workingSet;
    }
    
    /**
     * Determines the hit-rate of the cache.
     * 
     * @param cache      The self-evicting map.
     * @param workingSet The request working set.
     * @return           The hit-rate.
     */
    public static int determineEfficiency(Map<Integer, Integer> cache, List<Integer> workingSet) {
        int hits = 0;
        for (Integer key : workingSet) {
            if (cache.get(key) == null) {
                cache.put(key, 0);
            } else {
                hits++;
            }
        }
        return hits;
    }
    
    /**
     * Returns a pretty-printed string of the results.
     * 
     * @param type The name of the cache type.
     * @param size The size of the working set.
     * @param hits The hit-rate.
     */
    public static String prettyPrint(String type, int size, int hits) {
        int misses = size - hits;
        return String.format("%s: hits=%s (%s percent), misses=%s (%s percent)", type, 
                DecimalFormat.getInstance().format(hits), 
                DecimalFormat.getPercentInstance().format(hits/size),
                DecimalFormat.getInstance().format(misses), 
                DecimalFormat.getPercentInstance().format(misses/size));
    }
}
