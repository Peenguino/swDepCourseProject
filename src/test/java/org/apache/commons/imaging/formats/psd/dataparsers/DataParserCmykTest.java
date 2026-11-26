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

class DataParserCmykTest {

    @Test
    void testGetBasicChannelsCount() {
        final DataParserCmyk dataParserCmyk = new DataParserCmyk();

        assertEquals(4, dataParserCmyk.getBasicChannelsCount());
    }

    @Test
    void testParseDataInvertsStoredChannels() {
        // PSD stores CMYK inverted: all-255 means no ink (white), all-0 means full ink (black).
        final int[][][] whiteData = { { { 255 } }, { { 255 } }, { { 255 } }, { { 255 } } };
        final int[][][] blackData = { { { 0 } }, { { 0 } }, { { 0 } }, { { 0 } } };
        final PsdImageContents contents = new PsdImageContents(new PsdHeaderInfo(1, new byte[6], 4, 1, 1, 8, 4), 0, 0, 0, 0);

        final BufferedImage white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        new DataParserCmyk().parseData(whiteData, white, contents);
        assertEquals(ColorConversions.convertCmykToRgb(0, 0, 0, 0), white.getRGB(0, 0));
        assertEquals(0xffffffff, white.getRGB(0, 0));

        final BufferedImage black = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        new DataParserCmyk().parseData(blackData, black, contents);
        assertEquals(ColorConversions.convertCmykToRgb(255, 255, 255, 255), black.getRGB(0, 0));
        assertEquals(0xff000000, black.getRGB(0, 0));
    }

}
