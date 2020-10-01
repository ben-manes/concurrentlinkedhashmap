[...................................Caffeine](https://github.com/ben-manes/caffeine) is the Java 8 successor to ConcurrentLinkedHashMap and Guava's cache. Projects should
prefer Caffeine and migrate when requiring JDK8 or higher. The previous caching projects are supported in maintenance mode.

***
A high performance version of [java.util.LinkedHashMap](http://java.sun.com/javase/6/docs/api/java/util/LinkedHashMap.html) for use as a 
software cache. The project was migrated from its [old website](https://code.google.com/p/concurrentlinkedhashmap/) on Google Code.

# Design #
  * A linked list runs through a [ConcurrentHashMap](http://java.sun.com/javase/6/docs/api/java/util/concurrent/ConcurrentHashMap.html) to 
provide eviction ordering.
  * Avoids lock contention by amortizing the penalty under lock.

See the [design document](https://github.com/ben-manes/concurrentlinkedhashmap/wiki/Design) and the StrangeLoop conference 
[slides](http://concurrentlinkedhashmap.googlecode.com/files/ConcurrentCachingAtGoogle.pdf) ([Concurrent Caching at 
Google](https://thestrangeloop.com/sessions/concurrent-caching-with-mapmaker)).

# Features #
  * LRU page replacement policy (currently being upgraded to LIRS).
  * Equivalent performance to [ConcurrentHashMap](http://java.sun.com/javase/6/docs/api/java/util/concurrent/ConcurrentHashMap.html) under 
load.
  * Can bound by the size of the values (e.g. Multimap cache).
  * Can notify a listener when an entry is evicted.

See the [tutorial](https://github.com/ben-manes/concurrentlinkedhashmap/wiki/ExampleUsage) for examples of using this library.

# Status #
  * **Released v1.4.2** with _Least-Recently-Used_ page replacement policy.
  * Integrated into [Google Guava](http://code.google.com/p/guava-libraries/) 
([MapMaker](http://guava-libraries.googlecode.com/svn/trunk/javadoc/com/google/common/collect/MapMaker.html), 
[CacheBuilder](http://guava-libraries.googlecode.com/svn/trunk/javadoc/com/google/common/cache/CacheBuilder.html))

See the [Changelog](https://github.com/ben-manes/concurrentlinkedhashmap/wiki/Changelog) for version history.

## Future ##

  * v2.x: Implement [Low Inter-reference Recency Set](http://www.cse.ohio-state.edu/hpcs/WWW/HTML/publications/abs02-6.html) page replacement 
policy.

  * [Caffeine](https://github.com/ben-manes/caffeine): A Java 8 rewrite of Guava Cache is the current focus for further development.

See the [Changelog](https://github.com/ben-manes/concurrentlinkedhashmap/wiki/Changelog) for more details and current progress.

## Maven ##
Maven users should choose one of the dependencies based on their JDK version.

```
<!-- JDK 6 -->
<dependency>
  <groupId>com.googlecode.concurrentlinkedhashmap</groupId>
  <artifactId>concurrentlinkedhashmap-lru</artifactId>
  <version>1.4.2</version>
</dependency>

<!-- JDK 5 -->
<dependency>
  <groupId>com.googlecode.concurrentlinkedhashmap</groupId>
  <artifactId>concurrentlinkedhashmap-lru</artifactId>
  <version>1.2_jdk5</version>
</dependency>
```

# Performance #
In this benchmark an unbounded [ConcurrentHashMap](http://java.sun.com/javase/6/docs/api/java/util/concurrent/ConcurrentHashMap.html) is 
compared to a 
[ConcurrentLinkedHashMap](http://concurrentlinkedhashmap.googlecode.com/svn/wiki/release-1.3.1-LRU/com/googlecode/concurrentlinkedhashmap/ConcurrentLinkedHashMap.html) 
v1.0 with a maximum size of 5,000 entries under an artificially high load (250 threads, 4-cores).

![get](https://raw.githubusercontent.com/ben-manes/concurrentlinkedhashmap/wiki/images/performance/get.png)

![put](https://raw.githubusercontent.com/ben-manes/concurrentlinkedhashmap/wiki/images/performance/put.png)
