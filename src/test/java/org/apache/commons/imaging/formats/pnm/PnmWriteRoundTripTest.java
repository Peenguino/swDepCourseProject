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
package org.apache.commons.imaging.formats.pnm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

class PnmWriteRoundTripTest {

    private static BufferedImage blackAndWhiteImage() {
        // 9 columns exercise the partial trailing byte of raw PBM rows.
        final BufferedImage image = new BufferedImage(9, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? 0x000000 : 0xffffff);
            }
        }
        return image;
    }

    private static BufferedImage colorImage() {
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff0000);
        image.setRGB(1, 0, 0x00ff00);
        image.setRGB(0, 1, 0x0000ff);
        image.setRGB(1, 1, 0x808080);
        return image;
    }

    private static byte[] write(final BufferedImage src, final PnmImagingParameters params) throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new PnmImageParser().writeImage(src, os, params);
        return os.toByteArray();
    }

    private static BufferedImage read(final byte[] bytes) throws Exception {
        return new PnmImageParser().getBufferedImage(ByteSource.array(bytes), new PnmImagingParameters());
    }

    private static PnmImagingParameters params(final ImageFormats subtype, final boolean rawBits) {
        final PnmImagingParameters params = new PnmImagingParameters();
        params.setSubtype(subtype);
        params.setRawBits(rawBits);
        return params;
    }

    @Test
    void testPbmRawRoundTrip() throws Exception {
        final BufferedImage src = blackAndWhiteImage();
        final byte[] bytes = write(src, params(ImageFormats.PBM, true));
        assertEquals('4', bytes[1], "raw PBM must start with P4");

        final BufferedImage readBack = read(bytes);
        assertEquals(9, readBack.getWidth());
        assertEquals(2, readBack.getHeight());
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                final int expected = (x + y) % 2 == 0 ? 0xff000000 : 0xffffffff;
                assertEquals(expected, readBack.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void testPbmTextRoundTripDocumentsInversion() throws Exception {
        final BufferedImage src = blackAndWhiteImage();
        final byte[] bytes = write(src, params(ImageFormats.PBM, false));
        assertEquals('1', bytes[1], "text PBM must start with P1");

        // NOTE: the text PBM reader maps 1 to white and 0 to black, while the writer emits 1 for
        // dark pixels (as does the raw reader). The text round trip therefore inverts the image.
        final BufferedImage readBack = read(bytes);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                final int expected = (x + y) % 2 == 0 ? 0xffffffff : 0xff000000;
                assertEquals(expected, readBack.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void testPgmRawRoundTrip() throws Exception {
        final byte[] bytes = write(colorImage(), params(ImageFormats.PGM, true));
        assertEquals('5', bytes[1], "raw PGM must start with P5");

        final BufferedImage readBack = read(bytes);
        // Grayscale value is the integer average of the channels.
        assertEquals(0xff555555, readBack.getRGB(0, 0)); // (255+0+0)/3 = 85
        assertEquals(0xff555555, readBack.getRGB(1, 0)); // (0+255+0)/3 = 85
        assertEquals(0xff555555, readBack.getRGB(0, 1)); // (0+0+255)/3 = 85
        assertEquals(0xff808080, readBack.getRGB(1, 1));
    }

    @Test
    void testPgmTextRoundTrip() throws Exception {
        final byte[] bytes = write(colorImage(), params(ImageFormats.PGM, false));
        assertEquals('2', bytes[1], "text PGM must start with P2");

        final BufferedImage readBack = read(bytes);
        // Grayscale value is the integer average of the channels.
        assertEquals(0xff555555, readBack.getRGB(0, 0)); // (255+0+0)/3 = 85
        assertEquals(0xff555555, readBack.getRGB(1, 0)); // (0+255+0)/3 = 85
        assertEquals(0xff555555, readBack.getRGB(0, 1)); // (0+0+255)/3 = 85
        assertEquals(0xff808080, readBack.getRGB(1, 1));
    }

    @Test
    void testPpmRawRoundTrip() throws Exception {
        final BufferedImage src = colorImage();
        final byte[] bytes = write(src, params(ImageFormats.PPM, true));
        assertEquals('6', bytes[1], "raw PPM must start with P6");

        final BufferedImage readBack = read(bytes);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                assertEquals(0xff000000 | src.getRGB(x, y), readBack.getRGB(x, y));
            }
        }
    }

    @Test
    void testPpmTextRoundTrip() throws Exception {
        final BufferedImage src = colorImage();
        final byte[] bytes = write(src, params(ImageFormats.PPM, false));
        assertEquals('3', bytes[1], "text PPM must start with P3");

        final BufferedImage readBack = read(bytes);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                assertEquals(0xff000000 | src.getRGB(x, y), readBack.getRGB(x, y));
            }
        }
    }

    @Test
    void testNullParametersDefaultToRawPpm() throws Exception {
        final BufferedImage src = colorImage();
        final byte[] bytes = write(src, null);
        assertEquals('6', bytes[1]);

        final BufferedImage readBack = read(bytes);
        assertEquals(0xffff0000, readBack.getRGB(0, 0));
        assertEquals(0xff808080, readBack.getRGB(1, 1));
    }

    @Test
    void testTransparentImageDefaultsToPam() throws Exception {
        final BufferedImage src = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0, 0, 0x80ff0000);
        src.setRGB(1, 0, 0xffffffff);

        final byte[] bytes = write(src, new PnmImagingParameters());
        final String header = new String(bytes, 0, 60, StandardCharsets.US_ASCII);
        assertEquals('7', bytes[1], "images with alpha must be written as PAM");
        org.junit.jupiter.api.Assertions.assertTrue(header.contains("RGB_ALPHA"), header);

        final BufferedImage readBack = read(bytes);
        assertEquals(0x80ff0000, readBack.getRGB(0, 0));
        assertEquals(0xffffffff, readBack.getRGB(1, 0));
    }

    @Test
    void testReadPamGrayscale() throws Exception {
        final byte[] body = { 0, (byte) 255 };
        final byte[] header = "P7\n# a comment\nWIDTH 2\nHEIGHT 1\nDEPTH 1\nMAXVAL 255\nTUPLTYPE GRAYSCALE\nENDHDR\n".getBytes(StandardCharsets.US_ASCII);
        final byte[] bytes = concat(header, body);

        final BufferedImage image = read(bytes);
        assertEquals(0xff000000, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
    }

    @Test
    void testReadPamBlackAndWhiteAlpha() throws Exception {
        final byte[] body = { (byte) 255, 0, 0, (byte) 255 };
        final byte[] header = "P7\nWIDTH 2\nHEIGHT 1\nDEPTH 2\nMAXVAL 255\nTUPLTYPE BLACKANDWHITE_ALPHA\nENDHDR\n".getBytes(StandardCharsets.US_ASCII);
        final byte[] bytes = concat(header, body);

        final BufferedImage image = read(bytes);
        assertEquals(0x00ffffff, image.getRGB(0, 0));
        assertEquals(0xff000000, image.getRGB(1, 0));
    }

    @Test
    void testReadPpm16BitSamples() throws Exception {
        final byte[] body = { (byte) 255, (byte) 255, 0, 0, 0, 0 };
        final byte[] header = "P6\n1 1\n65535\n".getBytes(StandardCharsets.US_ASCII);
        final byte[] bytes = concat(header, body);

        final BufferedImage image = read(bytes);
        // Only the low 8 bits of the scaled 16-bit sample reach the output image.
        assertEquals(0xffff0000, image.getRGB(0, 0));
    }

    @Test
    void testReadPgm16BitSamples() throws Exception {
        final byte[] body = { (byte) 255, (byte) 255 };
        final byte[] header = "P5\n1 1\n65535\n".getBytes(StandardCharsets.US_ASCII);
        final byte[] bytes = concat(header, body);

        final BufferedImage image = read(bytes);
        assertEquals(0xffffffff, image.getRGB(0, 0));
    }

    private static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
