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

import org.apache.commons.imaging.color.ColorConversions;
import org.apache.commons.imaging.common.ImageBuilder;
import org.junit.jupiter.api.Test;

class PhotometricInterpreterCieLabTest {

    @Test
    void testInterpretPixelWritesConvertedColor() throws Exception {
        final PhotometricInterpreterCieLab interpreter = new PhotometricInterpreterCieLab(3, new int[] { 8, 8, 8 }, 1, 2, 2);
        final ImageBuilder imageBuilder = new ImageBuilder(2, 2, true);

        final int cieL = 53;
        final int cieA = 80;
        final int cieB = 67;
        interpreter.interpretPixel(imageBuilder, new int[] { cieL, cieA, cieB }, 1, 0);

        final int expected = ColorConversions.convertCieLabToArgbTest(cieL, (byte) cieA, (byte) cieB);
        assertEquals(expected, imageBuilder.getRgb(1, 0));
    }

    @Test
    void testInterpretPixelWithNeutralGray() throws Exception {
        final PhotometricInterpreterCieLab interpreter = new PhotometricInterpreterCieLab(3, new int[] { 8, 8, 8 }, 1, 1, 1);
        final ImageBuilder imageBuilder = new ImageBuilder(1, 1, false);

        // a* = b* = 0 encodes a neutral color: only luminance matters.
        interpreter.interpretPixel(imageBuilder, new int[] { 100, 0, 0 }, 0, 0);

        assertEquals(ColorConversions.convertCieLabToArgbTest(100, (byte) 0, (byte) 0), imageBuilder.getRgb(0, 0));
    }
}
