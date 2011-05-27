// Copyright 2011 Google Inc. All Rights Reserved.

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
