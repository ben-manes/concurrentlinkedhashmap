/*
 * Copyright 2012 Ben Manes. All Rights Reserved.
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

import java.util.List;
import java.util.Map;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.EfficiencyRun;
import com.googlecode.concurrentlinkedhashmap.caches.Cache;
import com.googlecode.concurrentlinkedhashmap.caches.CacheBuilder;
import com.googlecode.concurrentlinkedhashmap.generator.Generator;
import com.googlecode.concurrentlinkedhashmap.generator.ScrambledZipfianGenerator;
import org.testng.annotations.Test;

import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.createWorkingSet;
import static com.googlecode.concurrentlinkedhashmap.benchmark.Benchmarks.determineEfficiency;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * A unit-test for the LIRS page replacement policy.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public class LirsPolicyTest extends AbstractTest {

  @Test(enabled = false)
  public void efficiency() {
    Map<String, String> expected = new CacheBuilder()
        .maximumCapacity((int) capacity())
        .makeCache(Cache.Lirs);
    Map<String, String> actual = new Builder<String, String>()
        .maximumWeightedCapacity(capacity())
        .lirs(true)
        .build();

    Generator generator = new ScrambledZipfianGenerator(10 * capacity());
    List<String> workingSet = createWorkingSet(generator, 10 * (int) capacity());

    EfficiencyRun runExpected = determineEfficiency(expected, workingSet);
    EfficiencyRun runActual = determineEfficiency(actual, workingSet);

    String reason = String.format("Expected [%s] but was [%s]", runExpected, runActual);
    assertThat(reason, runActual.hitCount, is(runExpected.hitCount));
  }
}
