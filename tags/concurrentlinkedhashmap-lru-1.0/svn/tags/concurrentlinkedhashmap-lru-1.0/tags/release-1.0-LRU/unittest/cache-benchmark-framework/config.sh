#!/bin/bash

#see "$CACHE_ROOT/cache-products/cache.sh" for details

THIS_DIR="./cache-products/clhm-1.0.0"

#setting up classpath
for JAR in $THIS_DIR/lib/*
do
   CLASSPATH=$CLASSPATH:$JAR
done

CLASSPATH="$CLASSPATH:./classes/production/clhm-1.0.0"
CLASSPATH="$CLASSPATH:$THIS_DIR/conf"
#--classpath was set

#additional JVM options
JVM_OPTIONS="$JVM_OPTIONS -Djava.net.preferIPv4Stack=true" JVM_OPTIONS="$JVM_OPTIONS -DcacheBenchFwk.cacheWrapperClassName=org.cachebench.cachewrappers.CLHMCacheWrapper"

#Cliff Click drop-in replacement
#JVM_OPTIONS="$JVM_OPTIONS -Xbootclasspath/p:$THIS_DIR/boot/java_util_concurrent_chm.jar"
