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
package org.apache.commons.imaging.formats.xbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

class XbmImageParserTest {

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] xbm(final String body) {
        return ascii("#define test_width 8\n#define test_height 1\n" + body);
    }

    private static BufferedImage read(final byte[] bytes) throws Exception {
        return new XbmImageParser().getBufferedImage(ByteSource.array(bytes), new XbmImagingParameters());
    }

    private static ImagingException readFailure(final byte[] bytes) {
        return assertThrows(ImagingException.class, () -> read(bytes));
    }

    @Test
    void testWriteReadRoundTrip() throws Exception {
        final BufferedImage src = new BufferedImage(9, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                src.setRGB(x, y, (x + y) % 2 == 0 ? 0x000000 : 0xffffff);
            }
        }
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new XbmImageParser().writeImage(src, os, new XbmImagingParameters());

        final BufferedImage readBack = read(os.toByteArray());
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
    void testWriteLargeImageInsertsLineBreaks() throws Exception {
        // 16x16 pixels produce 32 data bytes, enough to trigger the 12-per-line wrapping.
        final BufferedImage src = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                src.setRGB(x, y, (x * y) % 5 == 0 ? 0x000000 : 0xffffff);
            }
        }
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new XbmImageParser().writeImage(src, os, new XbmImagingParameters());
        final String text = new String(os.toByteArray(), StandardCharsets.US_ASCII);
        assertTrue(text.contains("static unsigned char"), text);
        assertTrue(text.endsWith("};\n"), text);

        final BufferedImage readBack = read(os.toByteArray());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int expected = (x * y) % 5 == 0 ? 0xff000000 : 0xffffffff;
                assertEquals(expected, readBack.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void testReadShortValues() throws Exception {
        // 16-bit input, full width: both bytes of the value are used.
        final BufferedImage image = read(xbm("static unsigned short test_bits[] = {0x0001};"));
        assertEquals(0xff000000, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
    }

    @Test
    void testReadShortValuesAtRowEnd() throws Exception {
        // width = 8 with 16-bit input: the second byte must be discarded.
        final BufferedImage image = read(xbm("static unsigned short test_bits[] = {0x0001};"));
        assertEquals(8, image.getWidth());
        assertEquals(0xff000000, image.getRGB(0, 0));
    }

    @Test
    void testGetImageInfoWithCLiteralVariants() throws Exception {
        final byte[] bytes = ascii("#define test_width 0x10\n#define test_height 010\n"
                + "#define test_x_hot 0\n#define test_y_hot 0x2\n"
                + "static unsigned char test_bits[] = {0x00};");
        final ImageInfo info = new XbmImageParser().getImageInfo(ByteSource.array(bytes), new XbmImagingParameters());
        assertEquals(16, info.getWidth(), "hex width literal");
        assertEquals(8, info.getHeight(), "octal height literal");
        assertEquals(1, info.getBitsPerPixel());

        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            assertTrue(new XbmImageParser().dumpImageFile(pw, ByteSource.array(bytes)));
        }
        assertTrue(out.toString().contains("X hot: 0"), out.toString());
        assertTrue(out.toString().contains("Y hot: 2"), out.toString());
    }

    @Test
    void testDumpWithoutHotspot() throws Exception {
        final byte[] bytes = xbm("static unsigned char test_bits[] = {0x00};");
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            assertTrue(new XbmImageParser().dumpImageFile(pw, ByteSource.array(bytes)));
        }
        assertTrue(out.toString().contains("Width: 8"), out.toString());
        assertTrue(!out.toString().contains("X hot"), out.toString());
    }

    @Test
    void testMissingWidth() {
        final byte[] bytes = ascii("#define test_height 1\nstatic unsigned char test_bits[] = {0x00};");
        assertTrue(readFailure(bytes).getMessage().contains("width not found"));
    }

    @Test
    void testMissingHeight() {
        final byte[] bytes = ascii("#define test_width 8\nstatic unsigned char test_bits[] = {0x00};");
        assertTrue(readFailure(bytes).getMessage().contains("height not found"));
    }

    @Test
    void testMissingStaticToken() {
        assertTrue(readFailure(xbm("foo")).getMessage().contains("no 'static' token"));
    }

    @Test
    void testTruncatedAfterStatic() {
        assertTrue(readFailure(xbm("static")).getMessage().contains("no 'unsigned' or 'char' or 'short' token"));
    }

    @Test
    void testInvalidStorageType() {
        assertTrue(readFailure(xbm("static int test_bits[] = {0x00};")).getMessage().contains("no 'char' or 'short' token"));
    }

    @Test
    void testMissingVariableName() {
        assertTrue(readFailure(xbm("static unsigned char")).getMessage().contains("no variable name"));
    }

    @Test
    void testVariableNameStartsWithDigit() {
        assertTrue(readFailure(xbm("static unsigned char 1abc[] = {0x00};")).getMessage().contains("doesn't start with letter"));
    }

    @Test
    void testMissingOpenBracket() {
        assertTrue(readFailure(xbm("static unsigned char test_bits] = {0x00};")).getMessage().contains("no '[' token"));
    }

    @Test
    void testMissingCloseBracket() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[ = {0x00};")).getMessage().contains("no ']' token"));
    }

    @Test
    void testMissingEquals() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] {0x00};")).getMessage().contains("no '=' token"));
    }

    @Test
    void testMissingOpenBrace() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] = 0x00;")).getMessage().contains("no '{' token"));
    }

    @Test
    void testMissingHexValue() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] = {1};")).getMessage().contains("hex value missing"));
    }

    @Test
    void testHexValueTooLong() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] = {0xabcdef};")).getMessage().contains("hex value too long"));
    }

    @Test
    void testPrematureEndOfFile() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] = {0x01")).getMessage().contains("premature end of file"));
    }

    @Test
    void testPunctuationError() {
        assertTrue(readFailure(xbm("static unsigned char test_bits[] = {0x01;}")).getMessage().contains("punctuation error"));
    }

    @Test
    void testNameAndDefaults() {
        final XbmImageParser parser = new XbmImageParser();
        assertEquals("X BitMap", parser.getName());
        assertEquals("xbm", parser.getDefaultExtension());
        assertNotNull(parser.getDefaultParameters());
    }
}
