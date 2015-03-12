# Introduction #

A [Bloom filter](http://en.wikipedia.org/wiki/Bloom_filter) can be used as a probabilistic cache to determine a containment relation before retrieving the value from an insertion-only data store. While false positives are possible, it can be used as a filter to reduce the number of unnecessary lookups. This is done in a space and time efficient manner so that the membership queries can be performed in-memory, while the retrieval may require an I/O operation.

# Details #

This was written in 2009 as interview practice. It uses the Wang/Jenkins hash for its performance and double hashing to create multiple hashes. It is threadsafe, but not concurrent. A concurrent version could be written by using an AtomicLongArray, with lock striping to avoid racing insertions for the same element if maintaining the Set contract.

Please see Guava's [BloomFilter](http://code.google.com/p/guava-libraries/wiki/HashingExplained#BloomFilter) for a production-quality implementation.

```
import static java.lang.Long.bitCount;
import static java.lang.Long.toBinaryString;
import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.util.Arrays.copyOf;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe Bloom filter implementation that uses a restricted version of the
 * {@link Set} interface. All mutative operations are implemented by making a fresh
 * copy of the underlying array. The set does not support traversal or removal
 * operations and may report false positives on membership queries.
 * <p>
 * The copy-on-write model is ordinarily too costly, but due to the high read/write
 * ratio common to Bloom filters this may be <em>more</em> efficient than alternatives
 * when membership operations vastly out number mutations. It is useful when you cannot
 * or do not want to synchronize membership queries, yet need to preclude interference
 * among concurrent threads.
 * <p>
 * A Bloom filter is a space and time efficient probabilistic data structure that is used
 * to test whether an element is a member of a set. False positives are possible, but false
 * negatives are not. Elements can be added to the set, but not removed. The more elements
 * that are added to the set the larger the probability of false positives. While risking
 * false positives, Bloom filters have a space advantage over other data structures for
 * representing sets by not storing the data items.
 *
 * @author <a href="mailto:ben.manes@gmail.com">Ben Manes</a>
 */
public final class BloomFilter<E> extends AbstractSet<E> {
    private final AtomicStampedReference<long[]> ref;
    private final float probability;
    private final int capacity;
    private final int length;
    private final int hashes;
    private final Lock lock;
    private final int bits;

    /**
     * Creates a Bloom filter that can store up to an expected maximum capacity with an acceptable probability
     * that a membership query will result in a false positive. The filter will size itself based on the given
     * parameters.
     *
     * @param capacity    The expected maximum number of elements to be inserted into the Bloom filter.
     * @param probability The acceptable false positive probability for membership queries.
     */
    public BloomFilter(int capacity, float probability) {
        if ((capacity <= 0) || (probability <= 0) || (probability >= 1)) {
            throw new IllegalArgumentException();
        }
        this.capacity = max(capacity, Long.SIZE);
        this.bits = bits(capacity, probability);
        this.length = bits / Long.SIZE;
        this.lock = new ReentrantLock();
        this.hashes = numberOfHashes(capacity, bits);
        this.probability = probability(hashes, capacity, bits);
        this.ref = new AtomicStampedReference<long[]>(new long[length], 0);
    }

    /**
     * Calculates the <tt>false positive probability</tt> of the {@link #contains(Object)}
     * method returning <tt>true</tt> for an object that had not been inserted into the
     * Bloom filter.
     *
     * @param hashes   The number of hashing algorithms applied to an element.
     * @param capacity The estimated number of elements to be inserted into the Bloom filter.
     * @param bits     The number of bits that can be used for storing membership.
     * @return         The estimated false positive probability.
     */
    private static float probability(int hashes, int capacity, int bits) {
        return (float) pow((1 - exp(-hashes * ((double) capacity / bits))), hashes);
    }

    /**
     * Calculates the optimal number of hashing algorithms, k, at a given sizing to
     * minimize the probability.
     *
     * @param capacity The estimated number of elements to be inserted into the Bloom filter.
     * @param bits     The number of bits that can be used for storing membership.
     * @return         The optimal number of hashing functions.
     */
    private static int numberOfHashes(int capacity, int bits) {
        return (int) ceil((bits / capacity) * log(2));
    }

    /**
     * Calculates the required number of bits assuming an optimal number of hashing
     * algorithms are available. The optimal value is normalized to the closest word.
     *
     * @param capacity    The estimated number of elements to be inserted into the Bloom filter.
     * @param probability The desired false positive probability of a membership query.
     * @return            The required number of storage bits.
     */
    private static int bits(int capacity, float probability) {
        int optimal = (int) ceil(abs(capacity * log(probability) / pow(log(2), 2)));
        int offset = Long.SIZE - (optimal % Long.SIZE);
        return (offset == 0) ? optimal : (optimal + offset);
    }

    /**
     * Returns the <tt>false positive probability</tt> of the {@link #contains(Object)}
     * method returning <tt>true</tt> for an object that had not been inserted into the
     * Bloom filter.
     *
     * @return The false positive probability for membership queries.
     */
    public float probability() {
        return probability;
    }

    /**
     * Returns the expected maximum number of elements that may be inserted into the Bloom filter.
     * The Bloom filter's space efficiency has been optimized to support this capacity with the
     * false positive probability specified by {@link #probability()}.
     *
     * @return The expected maximum number of elements to be inserted into the Bloom filter.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return ref.getStamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        lock.lock();
        try {
            ref.set(new long[length], 0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object o) {
        int[] size = new int[1];
        long[] words = ref.get(size);
        if (size[0] == 0) {
            return false;
        }
        int[] indexes = indexes(o);
        for (int index : indexes) {
            if (!getAt(index, words)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(E o) {
        int[] indexes = indexes(o);
        boolean added = false;
        lock.lock();
        try {
            int[] size = new int[1];
            long[] words = copyOf(ref.get(size), length);
            for (int index : indexes) {
                added |= setAt(index, words);
            }
            if (added) {
                ref.set(words, ++size[0]);
            }
        } finally {
            lock.unlock();
        }
        return added;
    }

    /**
     * Retrieves the flag stored at the index location in the given array.
     *
     * @param index The bit location of the flag.
     * @param words The array to lookup in.
     * @return      The flag's value.
     */
    private boolean getAt(int index, long[] words) {
        int i = index / Long.SIZE;
        int bitIndex = index % Long.SIZE;
        return (words[i] & (1L << bitIndex)) != 0;
    }

    /**
     * Sets the flag stored at the index location in the given array.
     *
     * @param index The bit location of the flag.
     * @param words The array to update.
     * @return      If updated.
     */
    private boolean setAt(int index, long[] words) {
        int i = index / Long.SIZE;
        int bitIndex = index % Long.SIZE;
        long mask = (1L << bitIndex);
        if ((words[i] & mask) == 0) {
            words[i] |= mask;
            return true;
        }
        return false;
    }

    /**
     * Calculates the index position for the object.
     *
     * @param o The object.
     * @return  The index to the bit.
     */
    private int[] indexes(Object o) {
        // Double hashing allows calculating multiple index locations
        int hashCode = o.hashCode();
        int probe = 1 + abs(hashCode % length);
        int[] indexes = new int[hashes];
        int h = hash(hashCode);
        for (int i=0; i<hashes; i++) {
            indexes[i] = abs(h ^ i*probe) % bits;
        }
        return indexes;
    }

    /**
     * Doug Lea's universal hashing algorithm used in the collection libraries.
     */
    private int hash(int hashCode) {
        // Spread bits using variant of single-word Wang/Jenkins hash
        hashCode += (hashCode <<  15) ^ 0xffffcd7d;
        hashCode ^= (hashCode >>> 10);
        hashCode += (hashCode <<   3);
        hashCode ^= (hashCode >>>  6);
        hashCode += (hashCode <<   2) + (hashCode << 14);
        return hashCode ^ (hashCode >>> 16);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof BloomFilter<?>)) {
            return false;
        }
        BloomFilter<?> filter = (BloomFilter<?>) o;
        int[] size1 = new int[1];
        int[] size2 = new int[1];
        long[] words1 = ref.get(size1);
        long[] words2 = filter.ref.get(size2);
        return (size1[0] == size2[0]) && Arrays.equals(words1, words2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(ref.getReference());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        int size[] = new int[1];
        long[] words = ref.get(size);
        StringBuilder builder = new StringBuilder("{");
        builder.append("probability=").append(probability).append(", ")
               .append("hashes=").append(hashes).append(", ")
               .append("capacity=").append(capacity).append(", ")
               .append("size=").append(size[0]).append(", ")
               .append("bits=").append(bits).append(", ")
               .append("bit-count=").append(toBitCount(words)).append(", ")
               .append("value=").append(toBinaryArrayString(words))
               .append('}');
        return builder.toString();
    }

    /**
     * Calculates the population count, the number of one-bits, in the words.
     *
     * @param words The consistent view of the data.
     * @return      The number of one-bits in the two's complement binary representation.
     */
    private int toBitCount(long[] words) {
        int population = 0;
        for (long word : words) {
            population += bitCount(word);
        }
        return population;
    }

    /**
     * Creates a pretty-printed binary string representation of the data.
     *
     * @param words The consistent view of the data.
     * @return      A binary string representation.
     */
    private String toBinaryArrayString(long[] words) {
        StringBuilder builder = new StringBuilder(words.length);
        boolean trim = true;
        for (int i=words.length-1; i>=0; i--) {
            long word = words[i];
            if ((word == 0) && trim) {
                continue;
            }
            builder.append(toBinaryString(word));
            trim = false;
        }
        if (trim) {
            builder.append('0');
        }
        return builder.toString();
    }
}
```