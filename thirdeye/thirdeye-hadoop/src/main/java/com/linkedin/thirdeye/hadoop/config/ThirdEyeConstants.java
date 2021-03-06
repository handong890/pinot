package com.linkedin.thirdeye.hadoop.config;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public final class ThirdEyeConstants {
  public static final String TOPK_VALUES_FILE = "topk_values";
  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd-HHmmss");
  public static final String TOPK_DIMENSION_SUFFIX = "_topk";
  public static final String OTHER = "other";
  public static final String EMPTY_STRING = "";
  public static final Number EMPTY_NUMBER = 0;
  public static final String SEGMENT_JOINER = "_";
  public static final String AUTO_METRIC_COUNT = "__COUNT";
  public static final String FIELD_SEPARATOR = ",";
  public static final String TAR_SUFFIX = ".tar.gz";
  public static final String AVRO_SUFFIX = ".avro";
}
