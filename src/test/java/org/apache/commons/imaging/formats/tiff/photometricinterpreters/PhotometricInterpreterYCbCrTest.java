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
package org.apache.commons.imaging.formats.tiff.photometricinterpreters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.imaging.common.ImageBuilder;
import org.junit.jupiter.api.Test;

class PhotometricInterpreterYCbCrTest {

    @Test
    void testConvertYCbCrtoRgbBlack() {
        // Y = 16 with neutral chroma is the video-range black point.
        assertEquals(0xff000000, PhotometricInterpreterYCbCr.convertYCbCrtoRGB(16, 128, 128));
    }

    @Test
    void testConvertYCbCrtoRgbWhite() {
        // Y = 255 with neutral chroma saturates all channels to 255.
        assertEquals(0xffffffff, PhotometricInterpreterYCbCr.convertYCbCrtoRGB(255, 128, 128));
    }

    @Test
    void testConvertYCbCrtoRgbClampsOutOfRangeValues() {
        // Y = 0 with Cb = Cr = 0 drives red and blue below 0: they must clamp to 0,
        // while green stays at 135 (1.164 * -16 + 0.813 * 128 + 0.392 * 128).
        final int rgb = PhotometricInterpreterYCbCr.convertYCbCrtoRGB(0, 0, 0);
        assertEquals(0, rgb & 0x00ff00ff, "red and blue channels must clamp to 0");
        assertEquals(135, (rgb >> 8) & 0xff);
        assertEquals(0xff, rgb >>> 24);
    }

    @Test
    void testLimit() {
        assertEquals(0, PhotometricInterpreterYCbCr.limit(-5, 0, 255));
        assertEquals(255, PhotometricInterpreterYCbCr.limit(300, 0, 255));
        assertEquals(128, PhotometricInterpreterYCbCr.limit(128, 0, 255));
    }

    @Test
    void testInterpretPixelNeutralChroma() throws Exception {
        final PhotometricInterpreterYCbCr interpreter = new PhotometricInterpreterYCbCr(3, new int[] { 8, 8, 8 }, 1, 2, 2);
        final ImageBuilder imageBuilder = new ImageBuilder(2, 2, true);

        // Neutral chroma: R = G = B = Y.
        interpreter.interpretPixel(imageBuilder, new int[] { 128, 128, 128 }, 1, 1);

        assertEquals(0xff808080, imageBuilder.getRgb(1, 1));
    }

    @Test
    void testInterpretPixelBlueWithClamping() throws Exception {
        final PhotometricInterpreterYCbCr interpreter = new PhotometricInterpreterYCbCr(3, new int[] { 8, 8, 8 }, 1, 1, 1);
        final ImageBuilder imageBuilder = new ImageBuilder(1, 1, true);

        // Blue in YCbCr (BT.601): red and green clamp to 0, blue truncates to 254.
        interpreter.interpretPixel(imageBuilder, new int[] { 29, 255, 107 }, 0, 0);

        assertEquals(0xff0000fe, imageBuilder.getRgb(0, 0));
    }
}
