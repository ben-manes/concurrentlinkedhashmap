/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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
package org.cachebench;

import static com.google.common.base.Strings.emptyToNull;

import org.cachebench.reportgenerators.CsvStatisticReportGenerator;

import java.io.File;

/**
 * An extension of the report generator to simplify the file name.
 *
 * @author bmanes@google.com (Ben Manes)
 */
public final class CustomCsvStatisticReportGenerator extends CsvStatisticReportGenerator {

  @Override public void setOutputFile(String fileName) {
    if ("-generic-".equals(fileName)) {
      String type = System.getProperty("cacheBenchFwk.cache.type");
      String run = emptyToNull(System.getProperty("cacheBenchFwk.cache.run"));
      this.output = new File(type + (run == null ? "" : "-" + run) + ".csv");
    } else {
      super.setOutputFile(fileName);
    }
  }
}
