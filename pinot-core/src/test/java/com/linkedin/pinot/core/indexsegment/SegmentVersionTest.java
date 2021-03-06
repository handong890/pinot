/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linkedin.pinot.core.indexsegment;

import org.testng.Assert;
import org.testng.annotations.Test;
import com.linkedin.pinot.common.utils.CommonConstants;
import com.linkedin.pinot.core.indexsegment.generator.SegmentVersion;


public class SegmentVersionTest {
  @Test
  public void test1() {
    SegmentVersion version;
    version = SegmentVersion.fromStringOrDefault("v1");
    Assert.assertEquals(version, SegmentVersion.v1);
    version = SegmentVersion.fromStringOrDefault("v2");
    Assert.assertEquals(version, SegmentVersion.v2);
    version = SegmentVersion.fromStringOrDefault("v3");
    Assert.assertEquals(version, SegmentVersion.v3);
    version = SegmentVersion.fromStringOrDefault("badString");
    Assert.assertEquals(version.toString(), (CommonConstants.Server.DEFAULT_SEGMENT_FORMAT_VERSION));
    version = SegmentVersion.fromStringOrDefault(null);
    Assert.assertEquals(version.toString(), CommonConstants.Server.DEFAULT_SEGMENT_FORMAT_VERSION);
    version = SegmentVersion.fromStringOrDefault("");
    Assert.assertEquals(version.toString(), CommonConstants.Server.DEFAULT_SEGMENT_FORMAT_VERSION);

    Assert.assertTrue(SegmentVersion.v1.compareTo(SegmentVersion.v2) < 0);
    Assert.assertTrue(SegmentVersion.v2.compareTo(SegmentVersion.v2) == 0);
    Assert.assertTrue(SegmentVersion.v3.compareTo(SegmentVersion.v2) > 0);
  }
}
