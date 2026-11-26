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
package org.apache.commons.imaging.formats.psd.datareaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.commons.imaging.common.BinaryFileParser;
import org.apache.commons.imaging.formats.psd.PsdHeaderInfo;
import org.apache.commons.imaging.formats.psd.PsdImageContents;
import org.apache.commons.imaging.formats.psd.dataparsers.DataParserGrayscale;
import org.junit.jupiter.api.Test;

class UncompressedDataReaderTest {

    private static PsdImageContents grayscaleContents(final int columns, final int rows) {
        return new PsdImageContents(new PsdHeaderInfo(1, new byte[6], 1, rows, columns, 8, 1), 0, 0, 0, 0);
    }

    @Test
    void testReadDataGrayscale() throws Exception {
        // 2x2 grayscale image, 8 bits per sample, channel-planar layout.
        final byte[] samples = { 0, (byte) 255, 127, (byte) 128 };
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        try (InputStream is = new ByteArrayInputStream(samples)) {
            new UncompressedDataReader(new DataParserGrayscale()).readData(is, image, grayscaleContents(2, 2), new BinaryFileParser());
        }

        assertEquals(0xff000000, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
        assertEquals(0xff7f7f7f, image.getRGB(0, 1));
        assertEquals(0xff808080, image.getRGB(1, 1));
    }

    @Test
    void testReadDataTruncatedStream() throws Exception {
        // Only one sample available for a 2x1 image. NOTE: the current implementation does not
        // detect truncation: MyBitInputStream returns -1 at EOF, which becomes sample 255 (white).
        // This test documents the existing behavior; silently accepting truncated data is questionable.
        final byte[] samples = { 42 };
        final BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);

        try (InputStream is = new ByteArrayInputStream(samples)) {
            new UncompressedDataReader(new DataParserGrayscale()).readData(is, image, grayscaleContents(2, 1), new BinaryFileParser());
        }

        assertEquals(0xff2a2a2a, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
    }
}
