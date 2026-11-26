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
package org.apache.commons.imaging.formats.xpm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parses hand-written XPM sources to exercise the header, palette and error branches of {@link XpmImageParser}.
 */
class XpmParserBranchTest {

    private static final XpmImageParser PARSER = new XpmImageParser();

    private static ByteSource src(final String text) {
        return ByteSource.array(text.getBytes(StandardCharsets.US_ASCII));
    }

    /** Builds a well-formed XPM document from a values line and further string rows. */
    private static String xpm(final String values, final String... rows) {
        final StringBuilder sb = new StringBuilder("/* XPM */\nstatic char * img_1[] = {\n\"").append(values).append('"');
        for (final String row : rows) {
            sb.append(",\n\"").append(row).append('"');
        }
        return sb.append("};\n").toString();
    }

    @Test
    void testHexColorWidthsAndHotSpotWithExtension() throws IOException {
        // 9, 12 and 24 hex digit colors plus hotspot and XPMEXT in the values section
        // pure red, green and blue respectively; only the hue is asserted (see the precision note in the report)
        final String text = xpm("3 1 3 1 0 0 XPMEXT", "a c #FFF000000", "b c #0000FFFF0000", "c c #0000000000000000FFFFFFFF", "abc");

        final BufferedImage image = PARSER.getBufferedImage(src(text), null);

        assertEquals(3, image.getWidth());
        assertDominant(image.getRGB(0, 0), 16);
        assertDominant(image.getRGB(1, 0), 8);
        assertDominant(image.getRGB(2, 0), 0);

        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), src(text)));
        assertTrue(sw.toString().contains("XpmHeader"));
    }

    private static void assertDominant(final int argb, final int shift) {
        assertEquals(0xFF, argb >>> 24, "opaque");
        for (final int s : new int[] { 16, 8, 0 }) {
            final int channel = argb >> s & 0xFF;
            assertEquals(s == shift, channel != 0, "channel at shift " + s + " of " + Integer.toHexString(argb));
        }
    }

    @Test
    void testUnsupportedHexLengthGivesTransparentBlack() throws IOException {
        final BufferedImage image = PARSER.getBufferedImage(src(xpm("1 1 1 1", "a c #12345", "a")), null);
        assertEquals(0, image.getRGB(0, 0));
    }

    @Test
    void testGrayAndMonoKeysAreGrayscale() throws IOException {
        // "m" mono, "g4" 4-level gray and "g" gray keys; "light gray" is a multi-token color value
        final String text = xpm("3 1 3 1", "a m black", "b g4 gray50", "c g light gray", "abc");

        final ImageInfo info = PARSER.getImageInfo(src(text), null);

        assertEquals(ImageInfo.ColorType.GRAYSCALE, info.getColorType());
        final BufferedImage image = PARSER.getBufferedImage(src(text), null);
        assertEquals(0xFF000000, image.getRGB(0, 0));
    }

    @Test
    void testRgbAndStringContinuation() throws IOException {
        // the pixel row is split in two adjacent string literals, which are concatenated
        final String text = "/* XPM */\nstatic char *a_b[] = {\n\"2 1 2 1\",\n\"a c red\",\n\"b c None\",\n\"a\" \"b\"\n};\n";
        final ImageInfo info = PARSER.getImageInfo(src(text), null);
        assertEquals(ImageInfo.ColorType.RGB, info.getColorType());
        assertTrue(info.isTransparent());
        final BufferedImage image = PARSER.getBufferedImage(src(text), null);
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
        assertEquals(0, image.getRGB(1, 0) >>> 24);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/* XPN */\nstatic char * a[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nconst char * a[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic int * a[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char a[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * 1a[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a-b[] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a( = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[1] = {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] ; {\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = (\"1 1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {1, \"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1 2 3 4 5\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 x 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1 BAD\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 2 1\",\"a c red\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1\";\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1\",\"a c %123\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 2 1 1\",\"a c red\",\"a\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1\",\"a c red\",\"b\"};",
        "/* XPM */\nstatic char * a[] = {\"1 1 1 1\",\"a c red\",\"a\"}"
    })
    void testMalformedFilesAreRejected(final String text) {
        assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(src(text), null));
    }

    @Test
    void testWriteTransparentImage() throws IOException {
        final BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF00FF00);
        image.setRGB(1, 0, 0x00000000);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        PARSER.writeImage(image, os, null);
        final String written = os.toString(StandardCharsets.US_ASCII.name());

        assertTrue(written.contains("None"));
        final BufferedImage read = PARSER.getBufferedImage(ByteSource.array(os.toByteArray()), null);
        assertEquals(0xFF00FF00, read.getRGB(0, 0));
        assertEquals(0, read.getRGB(1, 0) >>> 24);
        assertFalse(PARSER.getImageInfo(ByteSource.array(os.toByteArray()), null).getColorType() == ImageInfo.ColorType.BW);
    }
}
