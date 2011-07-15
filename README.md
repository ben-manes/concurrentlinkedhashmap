ConcurrentLinkedHashMap
========================

ConcurrentLinkedHashMap is a concurrent, bounded map for use as an in-memory
cache. It is the default caching library in Apache Cassandra and is the
reference algorithm for Google Guava MapMaker.

Prior to Building
--------------------
The following libraries must be installed into the local repository. 

$ mvn install:install-file \
    -Dfile=lib/caliper/0.0/caliper.jar \
    -DgroupId=com.googlecode.caliper \
    -DartifactId=caliper \
    -Dversion=0.0 \
    -Dpackaging=jar

$ mvn install:install-file \
    -Dfile=lib/cache-benchmark/r7903/benchmark-fwk.jar \
    -DpomFile=lib/cache-benchmark/r7903/pom.xml

Compiling
--------------------
$ mvn clean compile

Analysis
--------------------
Static analysis and test coverage reports can be viewed by generating the site.
$ mvn site

Testing
--------------------
See testng.yaml for parameter configuration options of the unit tests and
benchmarks.

The default test suite is used for development.
$ mvn test (or mvn -P development test)

Load
--------------------
This test suite is used to detect problems that only appear after a long
execution, such as memory leaks.
$ mvn -P load test

Benchmarks
--------------------
At this time, this library does not supply a canonical benchmark. It leverages
existing benchmarks tools available externally.

This benchmarks the single-threaded performance.
$ mvn -P caliper test

This benchmarks the multi-threaded performance with an expected bound.
$ mvn -P cachebench test

This benchmarks the multi-threaded performance without an expected bound.
$ mvn -P perfHash test

This benchmarks the eviction algorithm efficiencies based on a scrambled zipfian
working set.
$ mvn -P efficiency test
