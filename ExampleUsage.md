## Maximum Capacity ##

A cache can be specified to hold up to 1,000 entries:

```
ConcurrentMap<K, V> cache = new ConcurrentLinkedHashMap.Builder<K, V>()
    .maximumWeightedCapacity(1000)
    .build();
```

Sometimes it is useful to allow entries to differ in the amount of capacity they consume. For example, a cache that holds collections may inadvertently leak memory by holding values that grow over time. By specifying a _weigher_, the cache can track these changes.

In this example, the cache is specified to hold up to 1,000 **edges**. Each element in the collection consumes a unit of capacity:
```
ConcurrentMap<Vertex, Set<Edge>> graphCache = new ConcurrentLinkedHashMap.Builder<Vertex, Set<Edge>>()
    .maximumWeightedCapacity(1000)
    .weigher(Weighers.<Edge>set())
    .build();
```

Similarly it may be useful to track based on the memory usage. The [Jamm](https://github.com/jbellis/jamm) library makes it simple:

```
EntryWeigher<K, V> memoryUsageWeigher = new EntryWeigher<K, V>() {
  final MemoryMeter meter = new MemoryMeter();

  @Override public int weightOf(K key, V value) {
    long bytes = meter.measure(key) + meter.measure(value);
    return (int) Math.min(bytes, Integer.MAX_VALUE);
  }
};
ConcurrentMap<K, V> cache = new ConcurrentLinkedHashMap.Builder<K, V>()
    .maximumWeightedCapacity(1024 * 1024) // 1 MB
    .weigher(memoryUsageWeigher)
    .build();
```

Note that the weighted size is calculated when it is added to the map, as caches typically hold immutable values. If the weighted size can change then the value must be reinserted, such as:

```
  // Inform the cache that the value's weight has changed, pick one of:
  cache.put(key, value); // add or replace the entry
  cache.replace(key, value); // replace if the entry exists
  cache.replace(key, value, value); // only replace if the same instance
```

The cache's weighted size and maximum capacity can be obtained by:

```
  System.out.println("Weighed size = " + cache.weightedSize());
  System.out.println("Capacity = " + cache.capacity());
```

The capacity can be changed and entries will be evicted if it currently exceeds the new threshold size:

```
  cache.setCapacity(500);
```

## Listening to Evictions ##

A listener can be notified after an entry is evicted:

```
EvictionListener<K, V> listener = new EvictionListener<K, V>() {
  @Override public void onEviction(K key, V value) {
    System.out.println("Evicted key=" + key + ", value=" + value);
  }
};
ConcurrentMap<K, V> cache = new ConcurrentLinkedHashMap.Builder<K, V>()
    .maximumWeightedCapacity(1000)
    .listener(listener)
    .build();
```

The listener is invoked by a thread operating on the map, such as the one that caused it to exceed the maximum size. However, sometimes this handling logic may be expensive or can throw an exception. The work can be performed asynchronously instead of exposing this detail to the calling thread:

```
EvictionListener<String, FileInputStream> listener = new EvictionListener<String, FileInputStream>() {
  final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override public void onEviction(String fileName, final FileInputStream stream) {
    executor.submit(new Callable<Void>() {
      @Override public Void call() throws IOException {
        stream.close();
        return null;
      }
    };
  }
};
ConcurrentMap<String, FileInputStream> cache = new ConcurrentLinkedHashMap.Builder<String, FileInputStream>()
    .maximumWeightedCapacity(1000)
    .listener(listener)
    .build();
```

## Advanced Optimizations ##
The keySet() and entrySet() iteration order are unspecified, but cheap to traverse. An ordered iteration can be useful to capture the hottest entries so that the caches can be warmed. A snapshot view in retention order can be obtained by:

```
  cache.descendingKeySet(); // all keys, ordered hot -> cold
  cache.descendingKeySetWithLimit(500); // the 500 hottest keys

  cache.descendingMap(); // all entries, ordered hot -> cold
  cache.descendingMapWithLimit(500); // the 500 hottest entries
```

[ConcurrentLinkedHashMap](http://concurrentlinkedhashmap.googlecode.com/svn/wiki/release-1.2-LRU/com/googlecode/concurrentlinkedhashmap/ConcurrentLinkedHashMap.html) is implemented as a decorator to [ConcurrentHashMap](http://java.sun.com/javase/6/docs/api/java/util/concurrent/ConcurrentHashMap.html) and inherits its concurrency characteristics. In large-scale systems the [NonBlockingHashMap](http://sourceforge.net/projects/high-scale-lib) may offer better concurrency characteristics. ConcurrentLinkedHashMap can be backed by NonBlockingHashMap by replacing the hash table implementation as described [here](http://high-scale-lib.cvs.sourceforge.net/viewvc/high-scale-lib/high-scale-lib/README).

```
  // See high scale-lib for more details
  java -Xbootclasspath/p:lib/java_util_concurrent_chm.jar
```