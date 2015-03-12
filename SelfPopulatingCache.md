# Introduction #

The [java.util.concurrent](http://java.sun.com/javase/6/docs/api/java/util/concurrent/package-summary.html) package makes it easy to implement the [Memoization](http://en.wikipedia.org/wiki/Memoization) idiom - a function that remembers the results of the input. The examples discussed here are further described in [Java Concurrency in Practice](http://jcip.net/).

# Single-item Memoization #

A [Future](http://java.sun.com/j2se/1.5.0/docs/api/java/util/concurrent/Future.html) provides a promise of the results of a computation that may be performed on some thread. It will block the _get()_ call until the result is available.

```
public class Example {
    private final FutureTask<Computation> future = new FutureTask<Computation>(new Computable());

    // Lazily computes the work and returns the value
    public Computation getComputation() throws Exception {
        future.run(); // no-ops on all subsequent calls
        return future.get();
    }

    private static final class Computable implements Callable<Computation> {
        public Computation call() {
          // do work, return
        }
    }
}
```

# Multi-item Memoization #

By using a Map multiple items can be lazily loaded. If unbounded this provides a configuration pool, while if bounded and self-evicting this provides a convenient cache.

```
public class SelfPopulatingMap<K, V> implements Map<K, V> {
    private final ConcurrentMap<K, Future<V>> map;
    private final EntryFactory<K, V> factory;

    public SelfPopulatingMap(ConcurrentMap<K, Future<V>> map, EntryFactory<K, V> factory) {
        this.map = map;
        this.factory = factory;
    }

    // Retrieves the value, creating it if needed.
    public V get(final K key) {
        FutureTask<V> task = new FutureTask<V>(
            new Callable<V>() {
                public V call() throws Exception {
                    return factory.createEntry(key);
            }
        });
        Future<V> future = map.putIfAbsent(key, task);
        if (future == null) {
            future = task;
            task.run();
        }
        try {
            return future.get();
        } catch (Exception e) {
            remove(key);
            throw new RuntimeException(e);
        }
    }

    public interface EntryFactory<K, V> {
        V createEntry(K key) throws Exception;
    }

    // further implementation details for a Map<K, V>
}
```

# A Multi-item Memoization Implementation #
The following shows a production-quality implementation, but to avoid introducing bugs it does not generalize out custom utilities. In addition to the single retrieval approach described above, it extends the concept to support bulk retrieval.

```
package com.reardencommerce.kernel.collections.shared;

import static com.reardencommerce.kernel.collections.shared.Maps2.forEach;
import static com.reardencommerce.kernel.collections.shared.Maps2.subMap;
import static com.reardencommerce.kernel.collections.shared.functions.Functions2.asFunction2ByArg2;
import static com.reardencommerce.kernel.collections.shared.functions.Functions2.asMapEntryFunction;
import static com.reardencommerce.kernel.concurrent.shared.Futures.asFuture;
import static com.reardencommerce.kernel.concurrent.shared.Futures.fromFuture;
import static com.reardencommerce.kernel.concurrent.shared.Futures.fromFutures;
import static com.reardencommerce.kernel.utilities.shared.Assertions.notNull;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import com.google.common.base.Function;
import com.google.common.collect.ForwardingConcurrentMap;
import com.reardencommerce.kernel.collections.shared.functions.Function2;
import com.reardencommerce.kernel.collections.shared.transforming.TransformingConcurrentMap;
import com.reardencommerce.kernel.collections.shared.transforming.TransformingSet;

/**
 * A {@link ConcurrentMap} that automatically creates an entry when not found and blocks other callers for that entry
 * during its creation process. Provides added optimizations if a bulk creation process is specified.
 * <p>
 * This implementation is not fully type-safe due to the inability to inspect the necessary type information at runtime.
 * For methods using raw object parameters, such as {@link #get(Object)}, a {@link ClassCastException} will be thrown
 * rather than returning a negative result on an invalid parameter type. This can be resolved by decorating with the
 * {@link com.reardencommerce.kernel.collections.shared.Maps2#checkedConcurrentMap(Map, Class, Class)}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 * @see <tt>Java Concurrency in Practice</tt>'s Memoizer example.
 */
public class SelfPopulatingMap<K, V> extends ForwardingConcurrentMap<K, V> {
    private static final Function2<?, ?, ?> decoder = asFunction2ByArg2(fromFuture());
    private static final Function2<?, ?, ?> encoder = asFunction2ByArg2(asFuture());
    private final TransformingConcurrentMap<K, Future<V>, V> delegate;
    private final ConcurrentMap<K, Future<V>> store;
    private final Loader<K, V> loader;

    /**
     * An unbounded implementation using a {@link ConcurrentHashMap}.
     *
     * @param factory The factory for creating entries.
     */
    public SelfPopulatingMap(EntryFactory<K, V> factory) {
        this(new ConcurrentHashMap<K, Future<V>>(), factory);
    }

    /**
     * An implementation that decorates the specified map.
     *
     * @param map     The backing map to decorate.
     * @param factory The factory for creating entries.
     */
    @SuppressWarnings("unchecked")
    public SelfPopulatingMap(ConcurrentMap<K, Future<V>> map, EntryFactory<K, V> factory) {
        this.delegate = new TransformingConcurrentMap<K, Future<V>, V>(map, (Function2<K, V, Future<V>>) encoder,
                                                                            (Function2<K, Future<V>, V>) decoder);
        this.loader = (factory instanceof BulkEntryFactory)
                            ? new ParallelLoader((BulkEntryFactory<K, V>) factory)
                            : new SerialLoader(factory);
        this.store = notNull(map);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        notNull(value);

        // Handles the lack of equals() being implemented by futures
        for (Future<V> future : store.values()) {
            try {
                if (future.isDone() && value.equals(future.get())) {
                    return true;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return false;
    }

    /**
     * Peeks into the backing map and retrieves the value if it exists.
     *
     * @param key The key whose associated value may be returned.
     * @return    The value or <tt>null</tt> if not found.
     */
    public V peek(Object key) {
        Future<V> future = store.get(notNull(key));
        return (future != null) && future.isDone()
                    ? fromFuture(future)
                    : null;
    }

    /**
     * Peeks into the backing map and retrieves the values for the given keys.
     *
     * @param keys The keys whose associated values may be returned.
     * @return     The entries for the specified keys.
     */
    public Map<K, V> peekAll(Collection<? extends K> keys) {
        return forEach(keys, new Function<K, V>() {
            public V apply(K key) {
                return peek(key);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        return loader.get(notNull((K) key));
    }

    /**
     * Retrieves a mapping of the given keys to their corresponding value.
     *
     * @param keys The keys whose associated values are to be returned.
     * @return     The entries for the specified keys.
     */
    public Map<K, V> getAll(Collection<? extends K> keys) {
        return loader.getAll(notNull(keys));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object key, Object value) {
        notNull(key, "Null key");
        notNull(value, "Null value");

        // Handles the lack of equals() being implemented by futures
        Future<V> current = store.get(key);
        return (current != null) && fromFuture(current).equals(value)
                    ? store.remove(key, current) // instance equals
                    : false;
    }

    /**
     * Removes the mapping for the keys if present.
     *
     * @param keys The keys for the entries to remove.
     */
    public void removeAll(Collection<? extends K> keys) {
        for (K key : keys) {
            store.remove(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        notNull(key, "Null key");
        notNull(oldValue, "Null old value");
        notNull(newValue, "Null new value");

        // Handles the lack of equals() being implemented by futures
        Future<V> current = store.get(key);
        return (current != null) && oldValue.equals(fromFuture(current))
                    ? store.replace(key, current, asFuture(newValue))
                    : false;
    }

    /**
     * Refreshes the entry by creating a new value and swapping it with the old value.
     * If a failure occurs then the previous entry will be removed.
     *
     * @param key The key.
     */
    public void refresh(K key) {
        try {
            V oldValue = peek(key);
            if (oldValue == null) {
                return;
            }

            V newValue = loader.createEntry(key);
            if (newValue == null) {
                store.remove(key);
            } else {
                replace(key, oldValue, newValue);
            }
        } catch (Exception e) {
            store.remove(key);
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final ConcurrentMap<K, V> delegate() {
        return delegate;
    }

    /**
     * A factory for creating a single entry.
     */
    public interface EntryFactory<K, V> {

        /**
         * Creates the entry for the map.
         *
         * @param key The key.
         * @return    The value.
         */
        V createEntry(K key) throws Exception;
    }

    /**
     * A factory with an optimized bulk creation strategy.
     */
    public interface BulkEntryFactory<K, V> extends EntryFactory<K, V> {

        /**
         * Creates entries for the map.
         *
         * @param keys The keys.
         * @return     The entries.
         */
        Map<K, V> createEntries(Collection<K> keys) throws Exception;
    }

    /**
     * Provides the retrieval strategy for the backing map. If the value is not found
     * it will be created and added back to the map.
     */
    private interface Loader<K, V> extends EntryFactory<K, V> {
        /**
         * Retrieves the value, creating it if necessary, and adds it to the map.
         */
        V get(K key);

        /**
         * Retrieves the values, creating them if necessary, and adds them to the map.
         */
        Map<K, V> getAll(Collection<? extends K> keys);
    }

    /**
     * A loader that is optimized for serial creation.
     */
    private class SerialLoader implements Loader<K, V> {
        private final EntryFactory<K, V> factory;

        public SerialLoader(EntryFactory<K, V> factory) {
            this.factory = notNull(factory);
        }

        /**
         * {@inheritDoc}
         */
        public final V createEntry(K key) throws Exception {
            return factory.createEntry(key);
        }

        /**
         * {@inheritDoc}
         */
        public final V get(final K key) {
            FutureTask<V> task = new FutureTask<V>(
                new Callable<V>() {
                    public V call() throws Exception {
                        return factory.createEntry(key);
                    }
                });
            Future<V> future = putIfAbsent(key, task);
            if (future == null) {
                future = task;
                task.run();
            }
            try {
                V value = fromFuture(future);
                if (value == null) {
                    store.remove(key, future);
                }
                return value;
            } catch (RuntimeException e) {
                store.remove(key, future);
                throw e;
            }
        }

        /**
         * An optimized {@link ConcurrentMap#putIfAbsent(Object, Object)} that performs a
         * non-blocking retrieval before falling back on a lock-based conditional insertion.
         */
        protected final Future<V> putIfAbsent(K key, Future<V> task) {
            Future<V> future = store.get(key);
            return (future == null) ? store.putIfAbsent(key, task)
                                    : future;
        }

        /**
         * {@inheritDoc}
         */
        public Map<K, V> getAll(Collection<? extends K> keys) {
            return subMap(keys, SelfPopulatingMap.this);
        }
    }

    /**
     * A loader that is optimized for parallel creation. For the values being created, all
     * subsequent calls for those keys are blocked while the bulk operation is being performed.
     */
    private final class ParallelLoader extends SerialLoader {
        private final BulkEntryFactory<K, V> factory;

        public ParallelLoader(BulkEntryFactory<K, V> factory) {
            super(factory);
            this.factory = notNull(factory);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<K, V> getAll(Collection<? extends K> keys) {
            // Sets up a bulk operation to be performed for the keys that are not found
            final List<K> creationKeys = new ArrayList<K>(keys.size());
            final FutureTask<Map<K, V>> task = new FutureTask<Map<K, V>>(
                new Callable<Map<K, V>>() {
                    public Map<K, V> call() throws Exception {
                        if (creationKeys.isEmpty()) {
                            return emptyMap();
                        } else if (creationKeys.size() == 1) {
                            K key = creationKeys.get(0);
                            return singletonMap(key, factory.createEntry(key));
                        }
                        return factory.createEntries(creationKeys);
                    }
                });

            // For any key not found, adds a proxy for retrieval once the the bulk operation
            // has completed. Also composes a map of all keys to their value holder.
            List<FutureTask<V>> proxies = new ArrayList<FutureTask<V>>(keys.size());
            Map<K, Future<V>> futures = new HashMap<K, Future<V>>(keys.size());
            for (K key : keys) {
                FutureTask<V> proxy = new FutureTask<V>(new Proxy(key, task));
                Future<V> future = putIfAbsent(key, proxy);
                if (future == null) {
                    future = proxy;
                    proxies.add(proxy);
                    creationKeys.add(key);
                }
                futures.put(key, future);
            }

            // Executes the bulk operation and runs the proxies so that they are warmed with their value
            task.run();
            for (FutureTask<V> proxy : proxies) {
                proxy.run();
            }
            return fromFutures(futures);
        }
    }

    /**
     * A single-item proxy to the results of a bulk operation.
     */
    private final class Proxy implements Callable<V> {
        private Future<Map<K, V>> task;
        private K key;

        public Proxy(K key, Future<Map<K, V>> task) {
            this.task = task;
            this.key = key;
        }
        public V call() throws Exception {
            try {
                V value = task.get().get(key);
                if (value == null) {
                    store.remove(key);
                }
                return value;
            } catch (Exception e) {
                store.remove(key);
                throw e;
            } finally {
                // allows GC
                task = null;
                key = null;
            }
        }
    }

    /**
     * An adapter to represent the data in the external type.
     */
    private final class EntrySet extends TransformingSet<Entry<K, Future<V>>, Entry<K, V>> {
        @SuppressWarnings("unchecked")
        public EntrySet() {
            super(SelfPopulatingMap.this.store.entrySet(),
                  asMapEntryFunction((Function2<K, V, Future<V>>) encoder),
                  asMapEntryFunction((Function2<K, Future<V>, V>) decoder));
        }
        @Override
        public boolean contains(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) obj;
            V value = peek(entry.getKey()); // non-blocking
            return (value != null) && (value.equals(entry.getValue()));
        }
        @Override
        public boolean add(Entry<K, V> entry) {
            return (putIfAbsent(entry.getKey(), entry.getValue()) == null);
        }
        @Override
        public boolean remove(Object o) {
            if (o instanceof Entry) {
                Entry<?, ?> entry = (Entry<?, ?>) o;
                return SelfPopulatingMap.this.remove(entry.getKey(), entry.getValue());
            }
            return false;
        }
    }
}
```

```
package com.reardencommerce.kernel.collections.shared;

import static com.reardencommerce.kernel.collections.shared.Collections3.combine;
import static com.reardencommerce.kernel.collections.shared.Maps2.entry;
import static com.reardencommerce.kernel.collections.shared.Maps2.forEach;
import static com.reardencommerce.kernel.concurrent.shared.ConcurrentTestHarness.timeTasks;
import static com.reardencommerce.kernel.concurrent.shared.Futures.asFuture;
import static com.reardencommerce.kernel.utilities.shared.Assertions.equalTo;
import static java.lang.Integer.valueOf;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.reardencommerce.kernel.collections.shared.SelfPopulatingMap.BulkEntryFactory;
import com.reardencommerce.kernel.collections.shared.SelfPopulatingMap.EntryFactory;

/**
 * A unit test for the {@link SelfPopulatingMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class SelfPopulatingMapTest {
    private final IdentityFactory<Integer> factory = new BulkIdentityFactory<Integer>();
    private final SelfPopulatingMap<Integer, Integer> map = new SelfPopulatingMap<Integer, Integer>(factory);

    @BeforeMethod
    public void reset() {
        map.clear();
        factory.count.set(0);
        factory.fail = false;
        factory.removed = false;
    }

    @Test
    public void clearSizeIsEmpty() {
        map.put(1, 1);
        assertFalse(map.isEmpty());
        assertEquals(map.size(), 1);

        map.clear();
        assertNull(map.peek(1));
        assertTrue(map.isEmpty());
        assertEquals(map.size(), 0);
    }

    @Test
    public void singular() {
        assertNull(map.peek(1));
        assertFalse(map.containsKey(1));
        assertFalse(map.containsValue(1));
        assertEquals(map.get(1), valueOf(1));
        assertEquals(map.put(1, 2), valueOf(1));
        assertEquals(map.get(1), valueOf(2));
        assertEquals(map.putIfAbsent(1, 3), valueOf(2));
        assertEquals(map.replace(1, 3), valueOf(2));
        assertEquals(map.remove(1), valueOf(3));
        assertNull(map.replace(1, 4));
        assertNull(map.putIfAbsent(1, 4));
        assertEquals(map.get(1), valueOf(4));
        assertEquals(map.peek(1), valueOf(4));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(4));
        assertFalse(map.replace(1, 3, 5));
        assertTrue(map.replace(1, 4, 5));
        assertFalse(map.remove(1, 6));
        assertTrue(map.remove(1, 5));

        factory.removed = true;
        assertNull(map.get(1));
    }

    @Test
    public void containsValueWithFailure() {
        Future<Integer> future = asFuture(new Exception());
        assertNull(getStore().put(1, future));
        assertFalse(map.containsValue(future));
    }

    @Test
    public void serialBulk() {
        IdentityFactory<Integer> serialFactory = new IdentityFactory<Integer>();
        doBulkTest(new SelfPopulatingMap<Integer, Integer>(serialFactory), serialFactory);
    }

    @Test
    public void parallelBulk() {
        doBulkTest(map, factory);
    }

    private void doBulkTest(SelfPopulatingMap<Integer, Integer> map, IdentityFactory<Integer> factory) {
        List<Integer> keys = asList(1, 2, 3, 4, 5);
        Map<Integer, Integer> entries = map.getAll(keys);
        assertTrue(entries.keySet().containsAll(keys));
        assertTrue(entries.values().containsAll(keys));
        assertEquals(entries.size(), keys.size());
        assertEquals(factory.count.get(), keys.size());

        int count = factory.count.get();
        assertEquals(map.getAll(keys), entries);
        assertEquals(factory.count.get(), count);
        assertEquals(map.peekAll(combine(keys, asList(6, 7, 8))), entries);

        map.removeAll(keys);
        assertTrue(map.isEmpty());

        map.putAll(entries);
        assertEquals(map, entries);

        assertTrue(map.keySet().containsAll(keys));
        assertTrue(map.values().containsAll(keys));

        map.clear();
        factory.removed = true;
        assertTrue(map.getAll(keys).isEmpty());
        assertTrue(map.isEmpty());
    }

    @Test
    public void refresh() {
        map.refresh(1);
        assertFalse(map.containsKey(1));
        assertNull(map.put(1, 5));
        map.refresh(1);
        assertEquals(map.peek(1), valueOf(1));

        factory.removed = true;
        map.refresh(1);
        assertNull(map.peek(1));
    }

    @Test(expectedExceptions=RuntimeException.class)
    public void refreshWithFailure() {
        assertNull(map.put(1, 5));
        factory.fail = true;
        try {
            map.refresh(1);
            fail();
        } finally {
            assertNull(map.peek(1));
        }
    }

    @Test
    public void failure() {
        factory.fail = true;

        // singular
        try {
            assertNull(map.get(1));
            fail("Expected an exception to be thrown");
        } catch (RuntimeException e) {
            assertNull(map.peek(1));
        }

        // bulk
        try {
            assertTrue(map.getAll(asList(1, 2, 3, 4)).isEmpty());
            fail("Expected an exception to be thrown");
        } catch (RuntimeException e) {
            assertTrue(map.peekAll(asList(1, 2, 3, 4)).isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void entrySet() {
        // contains, add, remove
        Set<Entry<Integer, Integer>> set = map.entrySet();
        assertFalse(set.contains(entry(1, 1)));
        assertFalse(set.containsAll((asList(entry(1, 1)))));
        assertTrue(set.add(entry(1, 1)));
        assertFalse(set.add(entry(1, 1)));
        assertEquals(set.size(), 1);
        assertTrue(set.contains(entry(1, 1)));
        assertTrue(set.containsAll((asList(entry(1, 1)))));
        assertFalse(set.contains(entry(2, 2)));
        assertFalse(set.remove(new Object()));
        assertTrue(set.remove(entry(1, 1)));
        assertFalse(set.remove(entry(1, 1)));
        assertTrue(set.isEmpty());

        // clear
        map.getAll(asList(1, 2, 3));
        assertFalse(set.isEmpty());
        set.clear();
        assertTrue(set.isEmpty());

        // toArray
        assertEquals(set.toArray().length, 0);
        assertEquals(set.toArray(new Entry[set.size()]).length, 0);
        map.putAll(ImmutableMap.of(1, 1, 2, 2, 3, 3));
        Object[] array1 = set.toArray();
        Entry<?, ?>[] array2 = set.toArray(new Entry[set.size()]);
        assertEquals(array1, array2);
        assertEquals(set.toArray().length, 3);

        // iterator
        map.putAll(ImmutableMap.of(1, 1, 2, 2, 3, 3));
        for (Iterator<Entry<Integer, Integer>> i=map.entrySet().iterator(); i.hasNext();) {
            assertNotNull(i.next());
            i.remove();
        }
        assertTrue(set.isEmpty());
        assertTrue(map.isEmpty());
    }

    @Test
    public void concurrent() throws InterruptedException {
        timeTasks(10, new Runnable() {
            public void run() {
                equalTo(map.get(1), valueOf(1));
            }
        });
        assertEquals(factory.count.get(), 1);
        assertEquals(map.size(), 1);
    }

    @Test
    public void concurrentBulk() throws InterruptedException {
        final List<Integer> keys = asList(1, 2, 3, 4, 5);
        final Map<Integer, Integer> entries = ImmutableMap.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5);
        timeTasks(10, new Runnable() {
            public void run() {
                equalTo(map.getAll(keys), entries);
            }
        });
        assertEquals(factory.count.get(), 5);
        assertEquals(map.size(), 5);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Integer, Future<Integer>> getStore() {
        try {
            Field field = SelfPopulatingMap.class.getDeclaredField("store");
            field.setAccessible(true);
            return (ConcurrentMap<Integer, Future<Integer>>) field.get(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class IdentityFactory<K> implements EntryFactory<K, K> {
        public AtomicInteger count = new AtomicInteger();
        public boolean fail;
        boolean removed;

        public K createEntry(K key) {
            checkForFailure();
            count.incrementAndGet();
            return removed ? null : key;
        }
        private void checkForFailure() {
            if (fail) {
                throw new RuntimeException();
            }
        }
    }

    private static final class BulkIdentityFactory<K> extends IdentityFactory<K> implements BulkEntryFactory<K, K> {
        public Map<K, K> createEntries(Collection<K> keys) {
            return forEach(keys, new Function<K, K>() {
                public K apply(K key) {
                    return createEntry(key);
                }
            });
        }
    }
}
```