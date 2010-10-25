/*
 * Copyright 2010 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

/**
 * A class that can determine the whether an entry should be evicted from the
 * map. An instance is invoked under the map's eviction lock and will not block
 * other threads from performing most common operations on the map.
 * <p>
 * An instance may be evaluated after every write operation on the map or
 * triggered directly with {@link ConcurrentLinkedHashMap#evictWith(CapacityLimiter)}
 * An implementation should be aware that the caller's thread will not expect
 * long execution times or failures as a side effect of the capacity limiter
 * being evaluated. Execution safety and a fast turn around time should be
 * considered when implementing this interface.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <tt>http://code.google.com/p/concurrentlinkedhashmap/</tt>
 */
public interface CapacityLimiter {

  /**
   * Determines whether an entry should be evicted from the specified map.
   *
   * @param map the map to evaluate for whether an eviction is required
   * @return <tt>true</tt> if an entry should be evicted from the map
   */
  @GuardedBy("map.evictionLock")
  boolean hasExceededCapacity(ConcurrentLinkedHashMap<?, ?> map);
}
