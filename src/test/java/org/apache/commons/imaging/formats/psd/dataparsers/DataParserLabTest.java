/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.imaging.formats.psd.dataparsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;

import org.apache.commons.imaging.color.ColorConversions;
import org.apache.commons.imaging.formats.psd.PsdHeaderInfo;
import org.apache.commons.imaging.formats.psd.PsdImageContents;
import org.junit.jupiter.api.Test;

class DataParserLabTest {

    @Test
    void testGetBasicChannelsCount() {
        final DataParserLab dataParserLab = new DataParserLab();

        assertEquals(3, dataParserLab.getBasicChannelsCount());
    }

    @Test
    void testParseDataSubtractsChromaBias() {
        // a* and b* are stored with a +128 bias; L* is used as-is.
        final int[][][] data = { { { 53 } }, { { 80 + 128 } }, { { 67 + 128 } } };
        final PsdImageContents contents = new PsdImageContents(new PsdHeaderInfo(1, new byte[6], 3, 1, 1, 8, 9), 0, 0, 0, 0);
        final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        new DataParserLab().parseData(data, image, contents);

        assertEquals(ColorConversions.convertCieLabToArgbTest(53, 80, 67), image.getRGB(0, 0));
    }

}
