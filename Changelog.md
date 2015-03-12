| **Version** | **Branch** | **Status** |
|:------------|:-----------|:-----------|
| 1.x | [master](http://code.google.com/p/concurrentlinkedhashmap/source/browse/) | Active; Stable |
| 2.x | [lirs](http://code.google.com/p/concurrentlinkedhashmap/source/browse/?name=lirs) | Active; Stable |

Please submit feature requests or feedback as an [issue](http://code.google.com/p/concurrentlinkedhashmap/issues/entry?template=Feature%20Request), through the [discussion group](http://groups.google.com/group/concurrentlinkedhashmap), or private [email](mailto:ben.manes@gmail.com).

## Caffeine (in development) ##

[Caffiene](https://github.com/ben-manes/caffeine) is a caching library based on Java 8 that includes a Guava Cache
compatible API. This project is in early development.

## Version 2.0 <tt>(Not Released)</tt> ##

_Done_
  * Refactor eviction policy handling to be (internally) pluggable during transition to keep tests passing
_In Progress_
  * Implement [Low Inter-reference Recency Set](http://www.cse.ohio-state.edu/hpcs/WWW/HTML/publications/abs02-6.html) page replacement policy
_Pending_
  * Remove LRU policy
    * May have to keep both policies due to memory overhead; need to check with Cassandra folks
  * Add TODO for adopting JDK8's Thread.getProcessorId() for striping the buffers, instead of by thread id which is the only practical way currently.

The LIRS policy combines recency and frequency to provide a better hit rate than an LRU policy. It does this without incurring a high penalty, is performed in O(1) time, and maintains its advantage across a wide range of workloads. The disadvantage of this policy is the implementation complexity and increased per-entry memory usage.

Previous attempts to implement LIRS failed due to a race condition when weighted entries are used. The current attempt will try to resolve this by leveraging the task replay logic to maintain a secondary weight that is guarded by the policy's lock. This will avoid racy reads when resizing the LIRS stack & queue due to the entry's weight being manipulated by a CAS operation.

## Version 1.4.2 <tt>(Released: 1/2015)</tt> ##
This version resolves excessive memory usage.
  * Remove padding as concurrency/memory trade-off not worth it` ([issue #43](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=#43))

## Version 1.4.1 <tt>(Released: 12/2014)</tt> ##
This version resolves a warning in OSGi containers.
  * Added optional import rule for `sun.misc.*` ([issue #40](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=#40), [issue #42](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=#42))

## Version 1.4 <tt>(Released: 8/2013)</tt> ##
This version improves performance by weakening the LRU policy. These improvements are being ported to Guava in [issue 1487](https://code.google.com/p/guava-libraries/issues/detail?id=1487).

  * Made the LRU node a nested class (formerly inner class) to reduce memory usage by avoiding the implicit reference to the outer class (accidental oversight).
  * Weakened LRU recency constraint (naive drain)
    * Improved performance (drops bounded height priority queue)
    * Inlined read task (as the Node) to avoid object creation & GC
    * Used a lossy ring buffer for recording reads (vs ConcurrentLinkedQueue)
  * Embedded JDK8's rewrite of ConcurrentHashMap for the backing map
  * Added padding to avoid false sharing
  * Added license and notice files ([issue 38](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=38))

## Version 1.3 ##
### 1.3.2 <tt>(Released: 12/2012)</tt> ###
This version resolved a licensing concern for IBM.

  * Replace embedded concurrency annotations with provided scoped dependency ([issue 37](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=37))

### 1.3.1 <tt>(Released: 7/2012)</tt> ###
This version aided the integration into JCS.

  * Added getQuietly() method for peeking into the cache ([issue 34](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=34))

### 1.3 <tt>(Released: 5/2012)</tt> ###
This version added minor features at the request of Apache Cassandra.

  * Add OSGi manifest ([issue 27](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=27))
  * Added EntryWeigher to weigh a key and value pair
  * Changed the maximum capacity and weighted size to long values ([issue 31](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=31), [issue 33](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=33))
  * Dropped experimental catchup executor (added #cleanUp() in Guava)
  * Adopted [Caliper](http://code.google.com/p/caliper/) for single-threaded benchmark (overhead compared to LinkedHashMap)
  * Adopted [YCSB](https://github.com/brianfrankcooper/YCSB) for efficiency tests
  * Fully migrated to Maven

## Version 1.2 <tt>(Released: 5/2011)</tt> ##

This version provides enhancements focused on improved performance characteristics.
  * Optimizations to minimize garbage collection overhead
  * Reduced the amortized catch-up penalty by using a bounded height priority queue
  * Improved concurrency by replacing lock striping with out-of-order execution handling and streamlined paths
  * Can optionally defer the catch-up penalty to an Executor instead amortizing on caller threads
  * Supports snapshot iteration in order of hotness
  * Removed CapacityLimiter (API change)

## Version 1.1 <tt>(Released: 11/2010)</tt> ##
This version provides incremental improvements based on user feedback.
  * Fixes [issue 20](https://code.google.com/p/concurrentlinkedhashmap/issues/detail?id=20), which identified a race condition when updating a value's weight.
  * Reduces memory usage by optimizing the buffering of recency operations.
  * Reduces read contention by improving how a recency buffer is selected.
  * Strict LRU reordering by a more intelligent draining algorithm.
  * Support for limiting the capacity through a plug-in strategy.

## Version 1.0 <tt>(Released: 4/2010)</tt> ##
The first official release is based on the observation that the state of the page replacement's data structure does not need to be strictly consistent with the hash-table. Instead of applying an LRU reorder operation immediately, the operations are buffered and applied in batches. The LRU list is guarded by a _try-lock_ to provide the simplicity of exclusive list reordering without creating contention since a thread's task can be queued for later execution. A write buffer and per-segment read buffers are provided to ensure non-blocking behavior when updating the LRU chain. The amortized cost of draining the buffers was determined to be negligible compared to an unbounded ConcurrentHashMap.

This implementation also introduces the concept of _weighted values_ to allow bounding the map by a constraint other than the number of entries.

Integration of the algorithmic techniques into [MapMaker](http://guava-libraries.googlecode.com/svn/trunk/javadoc/com/google/common/collect/MapMaker.html) will be released in [Google Guava r08](http://code.google.com/p/guava-libraries) and is heavily based on this version.

## Version 0.0: _Production_ ##
This version was provided as an intermediate solution to users that required a cache that was faster than LinkedHashMap, but that was not considered a valid solution to be dubbed an official release. It was made available so that users did not accidentally adopt the previous beta or build directly off of _/trunk_.

This implementation guarded the deque by a dedicated lock. It reduced contention compared to LinkedHashMap by shrinking the critical section and supporting the _Second Chance_ policy as a pseudo-LRU eviction algorithm. In the common case this policy provides a hit rate equivalent to an LRU but with the favorable concurrency behavior of a _FIFO_ policy. However, in certain workloads it may degrade to have a high eviction penalty.

## Version 0.0: Design-2 ##
This attempt implemented the algorithm based on a lock-free deque. It was not released due to a race condition.

## Version 0.0: _09/2008 beta_ ##
This first attempt explored the basic problems when implementing a concurrent caching library. It provided _FIFO_, _LRU_, and _Second Chance_ policies with a concurrent deque. This implementation was never officially released due to a race condition.