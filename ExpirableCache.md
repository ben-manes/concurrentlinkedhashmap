# Introduction #

A time-based eviction policy provides a useful alternative to a strictly bounded, invalidating cache. An eventual consistency approach is acceptable when updates do not need to be reflected immediately and a noticeable performance gain can be achieved.

## Defining the Policy ##

The common time-based policies are _time-to-live_ and _time-to-idle_. The time-to-live value indicates how long the entry can stay within the cache, regardless of its activity. The time-to-idle value indicates how long the entry can stay within the cache when inactive. A cache should strive to have a high active-content-ratio in addition to a high hit ratio, as otherwise it is wasting resources.

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static com.reardencommerce.kernel.utilities.shared.Assertions.notNegative;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeUnit;

/**
 * The expiration policy defines when to evict an entry using a time-based policy.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirationPolicy {
    private final long timeToIdle;
    private final long timeToLive;
    private final boolean eternal;

    /**
     * Creates an expiration policy.
     *
     * @param builder The builder to source the configuration from.
     */
    private ExpirationPolicy(Builder builder) {
        this.timeToIdle = builder.timeToIdle;
        this.timeToLive = builder.timeToLive;
        this.eternal = (timeToIdle == 0) && (timeToLive == 0);
    }

    /**
     * Retrieves the maximum amount of time that the element can idle, where zero indicates
     * that this metric is ignored when determining whether to expire.
     *
     * @param unit The unit of time.
     * @return     The duration of time that the entry can idle, in seconds.
     */
    public long getTimeToIdle(TimeUnit unit) {
        return unit.convert(timeToIdle, NANOSECONDS);
    }

    /**
     * Retrieves the maximum amount of time that the element can live, where zero indicates
     * that this metric is ignored when determining whether to expire.
     *
     * @param unit The unit of time.
     * @return     The maximum lifetime of the entry, in seconds.
     */
    public long getTimeToLive(TimeUnit unit) {
        return unit.convert(timeToLive, NANOSECONDS);
    }

    /**
     * Determines whether the policy is disabled by having no metrics set to determine expiration.
     *
     * @return If the policy allows entries to never expire.
     */
    public boolean isEternal() {
        return eternal;
    }

    /**
     * A builder for creating {@link ExpirationPolicy} instances.
     */
    public static final class Builder {
        private long timeToIdle;
        private long timeToLive;

        /**
         * The length time the entry can idle. If zero then it is not used for eviction.
         *
         * @param duration The idle time duration.
         * @param unit     The unit of time.
         * @return         The builder.
         */
        public Builder timeToIdle(long duration, TimeUnit unit) {
            this.timeToIdle = notNegative(unit.toNanos(duration));
            return this;
        }

        /**
         * The length time the entry can live. If zero then it is not used for eviction.
         *
         * @param duration The lifetime duration.
         * @param unit     The unit of time.
         * @return         The builder.
         */
        public Builder timeToLive(long duration, TimeUnit unit) {
            this.timeToLive = notNegative(unit.toNanos(duration));
            return this;
        }

        /**
         * Creates an {@link ExpirationPolicy} based on the builder's settings.
         *
         * @return An expiration policy.
         */
        public ExpirationPolicy build() {
            return new ExpirationPolicy(this);
        }
    }
}
```

### Expirable Entry ###

To evaluate an entry it must contain its creation time, last access time, and policy.

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static com.reardencommerce.kernel.utilities.shared.Assertions.greaterThan;
import static com.reardencommerce.kernel.utilities.shared.Assertions.notNull;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeUnit;

/**
 * An expirable entry in the data store. All times are based on <i>computer time</i>.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class Expirable<K, V> {
    private final K key;
    private final V value;
    private final long creationTime;
    private volatile long accessTime;
    private final ExpirationPolicy policy;

    /**
     * Creates an expirable entry using the current time as creation.
     *
     * @param key    The key.
     * @param value  The value.
     * @param policy The policy.
     */
    public Expirable(K key, V value, ExpirationPolicy policy) {
        this(key, value, policy, nanoTime(), NANOSECONDS);
    }

    /**
     * Creates an expirable entry with the specified creation time.
     *
     * @param key          The key.
     * @param value        The value.
     * @param policy       The policy.
     * @param creationTime The creation time.
     * @param unit         The unit of time.
     */
    public Expirable(K key, V value, ExpirationPolicy policy, long creationTime, TimeUnit unit) {
        this.creationTime = unit.toNanos(greaterThan(0, creationTime));
        this.policy = notNull(policy);
        this.accessTime = nanoTime();
        this.value = notNull(value);
        this.key = notNull(key);
    }

    /**
     * Retrieves the key.
     *
     * @return The key.
     */
    public K getKey() {
        return key;
    }

    /**
     * Retrieves the value.
     *
     * @return The value.
     */
    public V getValue() {
        return value;
    }

    /**
     * Retrieves the expiration policy.
     *
     * @return The policy.
     */
    public ExpirationPolicy getPolicy() {
        return policy;
    }

    /**
     * Retrieves the creation time of the entry.
     *
     * @param unit The unit of time.
     * @return     The creation time.
     */
    public long getCreationTime(TimeUnit unit) {
        return unit.convert(creationTime, NANOSECONDS);
    }

    /**
     * Retrieves the last time the entry was accessed.
     *
     * @param unit The unit of time.
     * @return     The last accessed time.
     */
    public long getAccessTime(TimeUnit unit) {
        return unit.convert(accessTime, NANOSECONDS);
    }

    /**
     * Sets the last access time of the entry.
     *
     * @param accessTime The access time, in milliseconds.
     */
    public void onAccess() {
        accessTime = nanoTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return format("key=%s, value=%s, creationTime=%dns, accessTime=%dns",
                      getKey(), getValue(), getCreationTime(NANOSECONDS), getAccessTime(NANOSECONDS));
    }
}
```

### Evaluation ###

An entry is evaluated on access or on a schedule to determine if it has expired.

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.base.Predicate;

/**
 * A predicate that determines whether the element has expired.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirationPredicate<K, V> implements Predicate<Expirable<K, V>> {

    /**
     * Determines whether the element has expired.
     *
     * @param expirable The element to check.
     * @return          If the element has expired.
     */
    public boolean apply(Expirable<K, V> expirable) {
        ExpirationPolicy policy = expirable.getPolicy();
        long currentTime = nanoTime();
        return !policy.isEternal() &&
               (isPastTime(policy.getTimeToIdle(NANOSECONDS), expirable.getAccessTime(NANOSECONDS), currentTime) ||
                isPastTime(policy.getTimeToLive(NANOSECONDS), expirable.getCreationTime(NANOSECONDS), currentTime));
    }

    /**
     * Determines whether the element has exceeded its policy's time window.
     *
     * @param policyTime  The policy's time window.
     * @param baseTime    The starting time.
     * @param currentTime The current time.
     * @return            If the window has past.
     */
    private boolean isPastTime(long policyTime, long baseTime, long currentTime) {
        return (policyTime != 0) && (currentTime > (policyTime + baseTime));
    }
}
```

### Expirable Map ###

As a decorator it conforms to the Map interface to allow composing with the SelfPopulatingMap and/or IndexMap, as well as with an evictable data store.

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static com.reardencommerce.kernel.collections.shared.functions.Functions2.asMapEntryFunction;
import static com.reardencommerce.kernel.collections.shared.listening.Listeners.nullListener2;
import static com.reardencommerce.kernel.utilities.shared.Assertions.notNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Predicate;
import com.reardencommerce.kernel.collections.shared.functions.Function2;
import com.reardencommerce.kernel.collections.shared.listening.Listener2;
import com.reardencommerce.kernel.collections.shared.transforming.TransformingSet;

/**
 * A {@link ConcurrentMap} that supports a time-based eviction strategy. When the entry expires it
 * is removed from the decorated map and an optionally supplied expiration listener is notified.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirableMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    private final ConcurrentMap<K, Expirable<K, V>> delegate;
    private final Function2<K, Expirable<K, V>, V> decoder;
    private final Function2<K, V, Expirable<K, V>> encoder;
    private final Predicate<Expirable<K, V>> predicate;
    private final ExpirationPolicy defaultPolicy;
    private final ScheduledExecutorService es;
    private final Listener2<K, V> listener;

    /**
     * Creates a decorator that applies the expiration policy.
     *
     * @param delegate      The concurrent map to decorate.
     * @param defaultPolicy The default expiration policy.
     * @param builder       The other settings.
     */
    private ExpirableMap(ConcurrentMap<K, Expirable<K, V>> delegate,
                         ExpirationPolicy defaultPolicy, Builder<K, V> builder) {
        this.es = builder.es;
        this.delegate = delegate;
        this.listener = builder.listener;
        this.predicate = builder.predicate;
        this.defaultPolicy = defaultPolicy;

        // entry set functions
        this.encoder = new Function2<K, V, Expirable<K, V>>() {
            public Expirable<K, V> apply(K key, V value) {
                return new Expirable<K, V>(key, value, ExpirableMap.this.defaultPolicy);
            }
        };
        this.decoder = new Function2<K, Expirable<K, V>, V>() {
            public V apply(K key, Expirable<K, V> expirable) {
                return expirable.getValue();
            }
        };
    }

    /**
     * Returns the number of key-value mappings in this map. This may include expired
     * entries that have not yet been purged, but due to being a {@link ConcurrentMap}
     * the clients should expect a weakly consistent value.
     *
     * @return The number of key-value mappings in this map.
     */
    @Override
    public int size() {
        return delegate.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        delegate.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        notNull(key);

        Expirable<K, V> expirable = delegate.get(key);
        if (expirable == null) {
            return false;
        } else if (hasExpired(expirable)) {
            handleExpiration(expirable);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        notNull(value);

        for (Expirable<K, V> expirable : delegate.values()) {
            if (expirable.getValue().equals(value)) {
                if (hasExpired(expirable)) {
                    handleExpiration(expirable);
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(Object key) {
        notNull(key);

        Expirable<K, V> expirable = delegate.get(key);
        if (expirable == null) {
            return null;
        } else if (hasExpired(expirable)) {
            handleExpiration(expirable);
            return null;
        }
        expirable.onAccess();
        scheduleTimeToIdle(expirable);
        return expirable.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public V putIfAbsent(K key, V value) {
        return putIfAbsent(key, value, defaultPolicy);
    }

    /**
     * A {@link #putIfAbsent(Object, Object)} operation where the entry is expired based
     * on the supplied policy rather than the default policy for the map.
     *
     * @param key    The key with which the specified value is to be associated.
     * @param value  The value to be associated with the specified key.
     * @param policy The expiration policy for the entry.
     * @return       The previous value associated with specified key, or <tt>null</tt>
     *               if there was no mapping for key.
     */
    public V putIfAbsent(K key, V value, ExpirationPolicy policy) {
        notNull(key, "Null key");
        notNull(value, "Null value");
        notNull(policy, "Null policy");

        for (;;) {
            Expirable<K, V> expirable = new Expirable<K, V>(key, value, policy);
            Expirable<K, V> old = delegate.putIfAbsent(key, expirable);
            if (old == null) {
                scheduleTimeToLive(expirable);
                scheduleTimeToIdle(expirable);
                return null;
            } else if (hasExpired(old)) {
                handleExpiration(old);
                continue;
            } else {
                expirable.onAccess();
                scheduleTimeToIdle(expirable);
                return old.getValue();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V put(K key, V value) {
        return put(key, value, defaultPolicy);
    }

    /**
     * A {@link #put(Object, Object)} operation where the entry is expired based
     * on the supplied policy rather than the default policy for the map.
     *
     * @param key    The key with which the specified value is to be associated.
     * @param value  The value to be associated with the specified key.
     * @param policy The expiration policy for the entry.
     * @return       The previous value associated with specified key, or <tt>null</tt>
     *               if there was no mapping for key.
     */
    public V put(K key, V value, ExpirationPolicy policy) {
        notNull(key, "Null key");
        notNull(value, "Null value");
        notNull(policy, "Null policy");

        Expirable<K, V> expirable = new Expirable<K, V>(key, value, policy);
        Expirable<K, V> old = delegate.put(key, expirable);
        scheduleTimeToLive(expirable);
        scheduleTimeToIdle(expirable);
        if (old == null) {
            return null;
        } else if (hasExpired(old)) {
            handleExpirationAfterRemoval(expirable);
            return null;
        }
        return old.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(Object key) {
        notNull(key);

        Expirable<K, V> old = delegate.remove(key);
        if (old == null) {
            return null;
        } else if (hasExpired(old)) {
            handleExpirationAfterRemoval(old);
            return null;
        }
        return old.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object key, Object value) {
        notNull(key, "Null key");
        notNull(value, "Null value");

        Expirable<K, V> old = delegate.get(key);
        if (old == null) {
            return false;
        } else if (hasExpired(old)) {
            handleExpiration(old);
            return false;
        } else if (old.getValue().equals(value)) {
            return delegate.remove(key, old);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public V replace(K key, V value) {
        return replace(key, value, defaultPolicy);
    }

    /**
     * A {@link #replace(Object, Object)} operation where the entry is expired based
     * on the supplied policy rather than the default policy for the map.
     *
     * @param key    The key with which the specified value is to be associated.
     * @param value  The value to be associated with the specified key.
     * @param policy The expiration policy for the entry.
     * @return       The previous value associated with specified key, or <tt>null</tt>
     *               if there was no mapping for key.
     */
    public V replace(K key, V value, ExpirationPolicy policy) {
        notNull(key, "Null key");
        notNull(value, "Null value");
        notNull(policy, "Null policy");

        // avoid replacing an expired entry
        Expirable<K, V> old = delegate.get(key);
        if (old == null) {
            return null;
        } else if (hasExpired(old)) {
            handleExpiration(old);
            return null;
        }

        // attempt replacement and schedule expiration tasks if successful
        Expirable<K, V> expirable = new Expirable<K, V>(key, value, policy);
        old = delegate.replace(key, expirable);
        if (old == null) {
            return null;
        }
        scheduleTimeToLive(expirable);
        scheduleTimeToIdle(expirable);
        return old.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean replace(K key, V oldValue, V newValue) {
        return replace(key, oldValue, newValue, defaultPolicy);
    }

    /**
     * A {@link #replace(Object, Object, Object)} operation where the entry is expired based
     * on the supplied policy rather than the default policy for the map.
     *
     * @param key      The key with which the specified value is associated.
     * @param oldValue The value expected to be associated with the specified key.
     * @param newValue The value to be associated with the specified key.
     * @param policy   The expiration policy for the entry.
     * @return         If the value was replaced.
     */
    public boolean replace(K key, V oldValue, V newValue, ExpirationPolicy policy) {
        notNull(key, "Null key");
        notNull(policy, "Null policy");
        notNull(oldValue, "Null old value");
        notNull(newValue, "Null new value");

        Expirable<K, V> old = delegate.get(key);
        if (old == null) {
            return false;
        } else if (hasExpired(old)) {
            handleExpiration(old);
            return false;
        } else if (!oldValue.equals(old.getValue())) {
            return false;
        }
        Expirable<K, V> expirable = new Expirable<K, V>(key, newValue, policy);
        if (delegate.replace(key, old, expirable)) {
            scheduleTimeToLive(expirable);
            scheduleTimeToIdle(expirable);
            return true;
        }
        return false;
    }

    /**
     * Schedules a task to attempt to evict the element if it has expired based on its time-to-idle policy.
     *
     * @param expirable The element.
     */
    private void scheduleTimeToIdle(Expirable<K, V> expirable) {
        long timeToIdle = expirable.getPolicy().getTimeToIdle(NANOSECONDS);
        if (timeToIdle != 0) {
            es.schedule(new ExpirationTask<K, V>(this, expirable), timeToIdle, NANOSECONDS);
        }
    }

    /**
     * Schedules a task to attempt to evict the element if it has expired based on its time-to-live policy.
     *
     * @param expirable The element.
     */
    private void scheduleTimeToLive(Expirable<K, V> expirable) {
        long timeToLive = expirable.getPolicy().getTimeToLive(NANOSECONDS);
        if (timeToLive != 0) {
            es.schedule(new ExpirationTask<K, V>(this, expirable), timeToLive, NANOSECONDS);
        }
    }

    /**
     * Determines whether the element has expired.
     *
     * @param expirable The element.
     * @return          If it has expired.
     */
    private boolean hasExpired(Expirable<K, V> expirable) {
        return predicate.apply(expirable);
    }

    /**
     * Attempts to remove the entry and, if successful, does post-removal processing.
     *
     * @param expirable The element.
     */
    private void handleExpiration(Expirable<K, V> expirable) {
        if (delegate.remove(expirable.getKey(), expirable)) {
            handleExpirationAfterRemoval(expirable);
        }
    }

    /**
     * Performs post-removal handling of an expired entry, such as by notifying the listener.
     *
     * @param expirable The element.
     */
    private void handleExpirationAfterRemoval(Expirable<K, V> expirable) {
        listener.onEvent(expirable.getKey(), expirable.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * A task that discards the entry if it has expired.
     */
    private static final class ExpirationTask<K, V> implements Runnable {
        private final WeakReference<Expirable<K, V>> expirableRef;
        private final WeakReference<ExpirableMap<K, V>> mapRef;

        public ExpirationTask(ExpirableMap<K, V> map, Expirable<K, V> expirable) {
            expirableRef = new WeakReference<Expirable<K, V>>(expirable);
            mapRef = new WeakReference<ExpirableMap<K, V>>(map);
        }

        /**
         * Determines whether the entry has expired and, if so, performs the eviction.
         */
        public void run() {
            ExpirableMap<K, V> map = mapRef.get();
            Expirable<K, V> expirable = expirableRef.get();
            if ((map != null) && (expirable != null)) {
                if (map.hasExpired(expirable)) {
                    map.handleExpiration(expirable);
                }
            }
        }
    }

    /**
     * An adapter to represent the data in the external type.
     */
    private final class EntrySet extends TransformingSet<Entry<K, Expirable<K, V>>, Entry<K, V>> {
        public EntrySet() {
            super(delegate.entrySet(), asMapEntryFunction(encoder), asMapEntryFunction(decoder));
        }
        @Override
        public boolean contains(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            V value = get(entry.getKey());
            return (value != null) && value.equals(entry.getValue());
        }
        @Override
        public boolean add(Entry<K, V> entry) {
            return (putIfAbsent(entry.getKey(), entry.getValue()) == null);
        }
        @Override
        public boolean remove(Object o) {
            if (o instanceof Entry) {
                Entry<?, ?> entry = (Entry<?, ?>) o;
                return ExpirableMap.this.remove(entry.getKey(), entry.getValue());
            }
            return false;
        }
    }

    /**
     * A builder for creating {@link ExpiringMap}s. As the builder instances can be reused, it is safe
     * to call {@link #build} multiple times to build multiple maps in series.
     */
    public static final class Builder<K, V> {
        private final ExpirationPolicy.Builder policyBuilder;
        private Predicate<Expirable<K, V>> predicate;
        private ScheduledExecutorService es;
        private Listener2<K, V> listener;

        public Builder() {
            policyBuilder = new ExpirationPolicy.Builder();
            predicate = new ExpirationPredicate<K, V>();
            listener = nullListener2();
        }

        /**
         * Specifies the predicate to evaluate the value in order to determine whether it has expired.
         * This is an optional setting, which allows special integration behavior.
         *
         * @param predicate The predicate to evaluate whether the entry has expired.
         * @return          The builder.
         */
        public Builder<K, V> expirationPredicate(Predicate<Expirable<K, V>> predicate) {
            this.predicate = notNull(predicate);
            return this;
        }

        /**
         * The length time the entry can idle. If zero then it is not used for eviction.
         *
         * @param duration The idle time duration.
         * @param unit     The unit of time.
         * @return         The builder.
         */
        public Builder<K, V> timeToIdle(long duration, TimeUnit unit) {
            policyBuilder.timeToIdle(duration, unit);
            return this;
        }

        /**
         * The length time the entry can live. If zero then it is not used for eviction.
         *
         * @param duration The lifetime duration.
         * @param unit     The unit of time.
         * @return         The builder.
         */
        public Builder<K, V> timeToLive(long duration, TimeUnit unit) {
            policyBuilder.timeToLive(duration, unit);
            return this;
        }

        /**
         * An optional listener that is notified when an entry expires.
         *
         * @param listener A listener.
         * @return         The builder.
         */
        public Builder<K, V> listener(Listener2<K, V> listener) {
            this.listener = notNull(listener);
            return this;
        }

        /**
         * An executor service to schedule a task to discard expired elements.
         *
         * @param es The executor service, to allow better thread re-use.
         * @return   The builder.
         */
        public Builder<K, V> executionService(ScheduledExecutorService es) {
            this.es = notNull(es);
            return this;
        }

        /**
         * Creates an {@link ExpirableMap} that decorates a {@link ConcurrentHashMap}.
         *
         * @return An {@link ExpirableMap}.
         */
        public ExpirableMap<K, V> build() {
            return build(new ConcurrentHashMap<K, Expirable<K, V>>());
        }

        /**
         * Creates an {@link ExpirableMap} backed by the delegate.
         *
         * @return An {@link ExpirableMap}.
         */
        public ExpirableMap<K, V> build(ConcurrentMap<K, Expirable<K, V>> delegate) {
            notNull(es, "A scheduled executor service must be specified");
            return new ExpirableMap<K, V>(delegate, policyBuilder.build(), this);
        }
    }
}
```

### Unit Tests ###

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.reardencommerce.kernel.collections.shared.expirable.ExpirationPolicy.Builder;

/**
 * A unit test for the {@link ExpirationPolicy}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirationPolicyTest {

    @Test
    public void timeToIdle() {
        ExpirationPolicy policy = new Builder().timeToIdle(100, NANOSECONDS).build();
        assertEquals(policy.getTimeToIdle(NANOSECONDS), 100);
        assertEquals(policy.getTimeToLive(NANOSECONDS), 0);
        assertFalse(policy.isEternal());
    }

    @Test
    public void timeToLive() {
        ExpirationPolicy policy = new Builder().timeToLive(100, NANOSECONDS).build();
        assertEquals(policy.getTimeToIdle(NANOSECONDS), 0);
        assertEquals(policy.getTimeToLive(NANOSECONDS), 100);
        assertFalse(policy.isEternal());
    }

    @Test
    public void isEternal() {
        ExpirationPolicy policy = new Builder().build();
        assertEquals(policy.getTimeToIdle(NANOSECONDS), 0);
        assertEquals(policy.getTimeToLive(NANOSECONDS), 0);
        assertTrue(policy.isEternal());
    }
}
```

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.reardencommerce.kernel.collections.shared.expirable.ExpirationPolicy.Builder;

/**
 * A unit test for {@link Expirable}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirableTest {
    private final Integer KEY = 100;
    private final Integer VALUE = 200;
    private final long DELTA = SECONDS.toNanos(1);
    private final ExpirationPolicy POLICY = new Builder().build();

    @Test
    public void createNow() {
        Expirable<Integer, Integer> expirable = new Expirable<Integer, Integer>(KEY, VALUE, POLICY);
        assertTrue(expirable.getCreationTime(NANOSECONDS) + DELTA > nanoTime());
        assertTrue(expirable.getCreationTime(NANOSECONDS) < nanoTime() + DELTA);
        assertTrue(expirable.getAccessTime(NANOSECONDS) + DELTA > nanoTime());
        assertTrue(expirable.getAccessTime(NANOSECONDS) < nanoTime() + DELTA);
        assertEquals(expirable.getKey(), KEY);
        assertEquals(expirable.getValue(), VALUE);
        assertSame(expirable.getPolicy(), POLICY);
        assertEquals(expirable.toString(), expirable.toString());
    }

    @Test
    public void createPrior() {
        Expirable<Integer, Integer> expirable = new Expirable<Integer, Integer>(KEY, VALUE, POLICY, 500, NANOSECONDS);
        assertEquals(expirable.getKey(), KEY);
        assertEquals(expirable.getValue(), VALUE);
        assertSame(expirable.getPolicy(), POLICY);
        assertEquals(expirable.getCreationTime(NANOSECONDS), 500);
        assertTrue(expirable.getAccessTime(NANOSECONDS) + DELTA > nanoTime());
        assertTrue(expirable.getAccessTime(NANOSECONDS) < nanoTime() + DELTA);
        assertEquals(expirable.toString(), expirable.toString());
    }

    @Test
    public void onAccess() throws InterruptedException {
        Expirable<Integer, Integer> expirable = new Expirable<Integer, Integer>(KEY, VALUE, POLICY);
        long lastAccessTime = expirable.getAccessTime(NANOSECONDS);
        Thread.sleep(100);
        expirable.onAccess();
        assertTrue(expirable.getAccessTime(NANOSECONDS) > lastAccessTime);
    }
}
```

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.reardencommerce.kernel.collections.shared.expirable.ExpirationPolicy.Builder;

/**
 * A unit test for {@link ExpirationPredicate}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirationPredicateTest {
    private final ExpirationPredicate<Integer, Integer> predicate = new ExpirationPredicate<Integer, Integer>();

    @Test
    public void notExpiredAsEternal() {
        ExpirationPolicy policy = new Builder().build();
        assertFalse(predicate.apply(newExpirable(policy)));
    }

    @Test
    public void notExpiredAsWithinTimeToIdle() {
        ExpirationPolicy policy = new Builder().timeToIdle(10, SECONDS).build();
        assertFalse(predicate.apply(newExpirable(policy)));
    }

    @Test
    public void notExpiredAsWithinTimeToLive() {
        ExpirationPolicy policy = new Builder().timeToLive(10, SECONDS).build();
        assertFalse(predicate.apply(newExpirable(policy)));
    }

    @Test
    public void expiredDueToIdlePolicy() throws InterruptedException {
        ExpirationPolicy policy = new Builder().timeToIdle(1, MILLISECONDS).build();
        Expirable<Integer, Integer> expirable = newExpirable(policy);
        Thread.sleep(100);
        assertTrue(predicate.apply(expirable));
    }

    @Test
    public void expiredDueToLifetimePolicy() throws InterruptedException {
        ExpirationPolicy policy = new Builder().timeToLive(1, MILLISECONDS).build();
        Expirable<Integer, Integer> expirable = newExpirable(policy);
        Thread.sleep(100);
        assertTrue(predicate.apply(expirable));
    }

    private Expirable<Integer, Integer> newExpirable(ExpirationPolicy policy) {
        return new Expirable<Integer, Integer>(100, 200, policy);
    }
}
```

```
package com.reardencommerce.kernel.collections.shared.expirable;

import static com.reardencommerce.kernel.collections.shared.Maps2.entry;
import static com.reardencommerce.kernel.concurrent.shared.DaemonExecutors.daemonThreadFactory;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.reardencommerce.kernel.collections.shared.expirable.ExpirableMap.Builder;
import com.reardencommerce.kernel.collections.shared.listening.Listener2;

/**
 * A unit test for the {@link ExpirableMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class ExpirableMapTest {
    private final Integer KEY = 100;
    private final Integer KEY2 = 101;
    private final Integer VALUE = 200;
    private final Integer VALUE2 = 201;
    private final long TIME_TO_IDLE = 300;
    private final long TIME_TO_LIVE = 301;
    private final ScheduledThreadPoolExecutor es;

    private ExpirableMap<Integer, Integer> map;
    private NotificationListener listener;
    private TestablePredicate predicate;

    public ExpirableMapTest() {
        es = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("expiration-test"));
    }

    @BeforeMethod
    public void before() {
        predicate = new TestablePredicate();
        listener = new NotificationListener();
        Builder<Integer, Integer> builder = new Builder<Integer, Integer>();
        builder.expirationPredicate(predicate);
        builder.timeToIdle(TIME_TO_IDLE, SECONDS);
        builder.timeToLive(TIME_TO_LIVE, SECONDS);
        builder.executionService(es);
        builder.listener(listener);
        map = builder.build();
        es.getQueue().clear();
    }

    @AfterClass
    public void after() {
        es.shutdownNow();
    }

    @Test
    public void size() {
        assertEquals(map.size(), 0);
        assertNull(map.put(KEY, VALUE));
        assertEquals(map.size(), 1);
        predicate.hasExpired = true;
        assertEquals(map.size(), 1); // weakly consistent
        assertFalse(listener.notified);
        assertNull(map.get(KEY));    // forces eviction
        assertTrue(listener.notified);
        assertEquals(map.size(), 0); // now as expected
    }

    @Test
    public void clear() {
        map.clear();
        assertTrue(map.isEmpty());
        assertNull(map.put(KEY, VALUE));
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
        assertFalse(listener.notified);
    }

    @Test
    public void containsKey() {
        assertFalse(map.containsKey(KEY));
        assertNull(map.put(KEY, VALUE));
        assertTrue(map.containsKey(KEY));
        predicate.hasExpired = true;
        assertFalse(listener.notified);
        assertFalse(map.containsKey(KEY));
        assertTrue(listener.notified);
    }

    @Test
    public void containsValue() {
        assertNull(map.put(KEY, VALUE));
        assertTrue(map.containsValue(VALUE));
        assertFalse(map.containsValue(VALUE2));
        predicate.hasExpired = true;
        assertFalse(map.containsValue(VALUE));
        assertTrue(listener.notified);
    }

    @Test
    public void containsValueWithDuplicates() {
        assertNull(map.put(KEY, VALUE));
        assertNull(map.put(KEY2, VALUE));
        assertTrue(map.containsValue(VALUE));
        predicate.delegate = new Predicate<Expirable<Integer, Integer>>() {
            public boolean apply(Expirable<Integer, Integer> expirable) {
                return KEY.equals(expirable.getKey());
            }
        };
        assertTrue(map.containsValue(VALUE));
        assertFalse(map.containsKey(KEY));
        assertTrue(map.containsKey(KEY2));
        assertTrue(listener.notified);

        predicate.delegate = new Predicate<Expirable<Integer, Integer>>() {
            public boolean apply(Expirable<Integer, Integer> expirable) {
                return KEY2.equals(expirable.getKey());
            }
        };
        assertFalse(map.containsValue(VALUE));
        assertFalse(map.containsKey(KEY2));
    }

    @Test
    public void get() {
        assertNull(map.get(KEY));
        assertNull(map.put(KEY, VALUE));
        es.getQueue().clear();
        assertEquals(map.get(KEY), VALUE);
        assertEquals(es.getQueue().size(), 1); // assert idle task added
        assertEquals(map.get(KEY), VALUE);     // assert no side-effect
        predicate.hasExpired = true;
        assertNull(map.get(KEY));
        assertTrue(listener.notified);
    }

    @Test
    public void putIfAbsent() {
        assertNull(map.putIfAbsent(KEY, VALUE));
        assertEquals(es.getQueue().size(), 2); // assert tasks added
        assertEquals(map.putIfAbsent(KEY, VALUE2), VALUE);
        assertEquals(es.getQueue().size(), 3); // assert idle task added
        predicate.hasExpired = true;
        assertNull(map.putIfAbsent(KEY, VALUE));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        listener.notified = false;
        assertEquals(map.putIfAbsent(KEY, VALUE2), VALUE);
        assertFalse(listener.notified);
    }

    @Test
    public void put() {
        assertNull(map.put(KEY, VALUE));
        assertEquals(es.getQueue().size(), 2); // assert tasks added
        assertEquals(map.put(KEY, VALUE2), VALUE);
        assertEquals(es.getQueue().size(), 4); // assert tasks added
        assertEquals(map.get(KEY), VALUE2);
        predicate.hasExpired = true;
        assertNull(map.put(KEY, VALUE));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        listener.notified = false;
        assertEquals(map.get(KEY), VALUE);
        assertFalse(listener.notified);
    }

    @Test
    public void removeWithoutExpiration() {
        assertNull(map.remove(KEY));
        assertNull(map.put(KEY, VALUE));
        assertEquals(map.remove(KEY), VALUE);
        assertNull(map.get(KEY));
        assertFalse(listener.notified);
    }

    @Test
    public void removeWithExpiration() {
        assertNull(map.remove(KEY));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertNull(map.remove(KEY));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void removeConditionallyWithoutExpiration() {
        assertFalse(map.remove(KEY, VALUE));
        assertNull(map.put(KEY, VALUE));
        assertFalse(map.remove(KEY, VALUE2));
        assertTrue(map.remove(KEY, VALUE));
        assertNull(map.get(KEY));
        assertFalse(listener.notified);
    }

    @Test
    public void removeConditionallyWithExpirationOnMiss() {
        assertFalse(map.remove(KEY, VALUE));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertFalse(map.remove(KEY, VALUE2));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void removeConditionallyWithExpirationOnHit() {
        assertFalse(map.remove(KEY, VALUE));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertFalse(map.remove(KEY, VALUE));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void replaceWithoutExpiration() {
        assertNull(map.replace(KEY, VALUE));
        assertNull(map.put(KEY, VALUE));
        es.getQueue().clear();
        assertEquals(map.replace(KEY, VALUE2), VALUE);
        assertEquals(es.getQueue().size(), 2); // assert tasks added
        assertEquals(map.get(KEY), VALUE2);
        assertFalse(listener.notified);
    }

    @Test
    public void replaceWithExpiration() {
        assertNull(map.replace(KEY, VALUE));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertNull(map.replace(KEY, VALUE2));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void replaceConditionallyWithoutExpiration() {
        assertFalse(map.replace(KEY, VALUE, VALUE2));
        assertNull(map.put(KEY, VALUE));
        es.getQueue().clear();
        assertFalse(map.replace(KEY, VALUE2, VALUE));
        assertTrue(map.replace(KEY, VALUE, VALUE2));
        assertEquals(es.getQueue().size(), 2); // assert tasks added
        assertEquals(map.get(KEY), VALUE2);
        assertFalse(listener.notified);
    }

    @Test
    public void replaceConditionallyWithExpirationOnMiss() {
        assertFalse(map.replace(KEY, VALUE, VALUE2));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertFalse(map.replace(KEY, VALUE2, VALUE));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void replaceConditionallyWithExpirationOnHit() {
        assertFalse(map.replace(KEY, VALUE, VALUE2));
        assertNull(map.put(KEY, VALUE));
        predicate.hasExpired = true;
        assertFalse(map.replace(KEY, VALUE, VALUE2));
        assertTrue(listener.notified);
        predicate.hasExpired = false;
        assertNull(map.get(KEY));
    }

    @Test
    public void keySet() {
        map.putAll(ImmutableMap.of(1, 1, 2, 2, 3, 3));
        assertTrue(map.keySet().containsAll(asList(1, 2, 3)));
        assertEquals(map.keySet().size(), 3);
        assertFalse(listener.notified);
    }

    @Test
    public void entrySet() {
        map.putAll(ImmutableMap.of(1, 1, 2, 2, 3, 3));
        Entry<Integer, Integer> newEntry = entry(4, 4);
        Set<Entry<Integer, Integer>> entrySet = map.entrySet();
        assertEquals(entrySet.size(), 3);
        assertFalse(entrySet.contains(newEntry));
        assertTrue(entrySet.add(newEntry));
        assertFalse(entrySet.add(newEntry));
        assertTrue(entrySet.contains(newEntry));
        assertTrue(entrySet.remove(newEntry));
        assertFalse(entrySet.remove(newEntry));

        for (Entry<Integer, Integer> entry : entrySet) {
            assertTrue(entrySet.contains(entry));
        }
        assertFalse(entrySet.contains(new Object()));
        assertFalse(entrySet.remove(new Object()));
        assertFalse(listener.notified);
    }

    @Test
    public void scheduledExpiration() throws Exception {
        ExpirationPolicy policy = new ExpirationPolicy.Builder().timeToIdle(10, MILLISECONDS).build();
        predicate.hasExpired = true;
        map.put(1, 1, policy);
        assertEquals(es.getQueue().size(), 1);
        Thread.sleep(100);
        assertTrue(es.getQueue().isEmpty());
        assertTrue(map.isEmpty(), map.toString());
        assertTrue(listener.notified);
    }

    private final class TestablePredicate implements Predicate<Expirable<Integer, Integer>> {
        volatile Predicate<Expirable<Integer, Integer>> delegate = Predicates.alwaysFalse();
        volatile boolean hasExpired;

        public boolean apply(Expirable<Integer, Integer> expirable) {
            assertNotNull(expirable);
            return hasExpired || delegate.apply(expirable);
        }
    }

    private final class NotificationListener implements Listener2<Integer, Integer> {
        volatile boolean notified;
        public void onEvent(Integer key, Integer value) {
            notified = true;
        }
    }
}
```