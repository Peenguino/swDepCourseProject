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
package org.apache.commons.imaging.formats.pcx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Stream;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round-trips images through every bit depth / plane layout of {@link PcxWriter}, with and without RLE, and checks header error branches.
 */
class PcxWriteReadBranchTest {

    private static final PcxImageParser PARSER = new PcxImageParser();

    /** Image with exactly {@code colors} distinct opaque colors; the width is odd to exercise padding. */
    private static BufferedImage image(final int colors, final boolean blackAndWhite) {
        final BufferedImage image = new BufferedImage(41, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int i = (x + y * image.getWidth()) % colors;
                final int rgb = blackAndWhite ? i == 0 ? 0 : 0xFFFFFF : i * 0x010305 + 0x10C0E0;
                // the last row is a single long run, to exercise RLE repeat counts
                image.setRGB(x, y, y == image.getHeight() - 1 ? image.getRGB(0, 0) : rgb);
            }
        }
        return image;
    }

    static Stream<Arguments> layouts() {
        return Stream.of(
                // colors, B/W, bitDepth wanted, planes wanted, expected bpp, expected planes
                Arguments.of(2, true, 0, 0, 1, 1),
                Arguments.of(2, false, 0, 0, 1, 2),
                Arguments.of(4, false, 0, 2, 1, 2),
                Arguments.of(4, false, 0, 0, 2, 1),
                Arguments.of(6, false, 0, 0, 1, 3),
                Arguments.of(12, false, 0, 0, 1, 4),
                Arguments.of(12, false, 0, 1, 4, 1),
                Arguments.of(100, false, 0, 0, 8, 1),
                Arguments.of(600, false, 0, 0, 8, 3),
                Arguments.of(600, false, 24, 0, 8, 3));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testRoundTripAllLayouts(final int colors, final boolean bw, final int bitDepth, final int planes, final int expectedBpp, final int expectedPlanes)
            throws IOException {
        for (final int compression : new int[] { PcxConstants.PCX_COMPRESSION_UNCOMPRESSED, PcxConstants.PCX_COMPRESSION_RLE }) {
            final BufferedImage source = image(colors, bw);
            final PcxImagingParameters params = new PcxImagingParameters().setCompression(compression).setBitDepth(bitDepth).setPlanes(planes);

            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            PARSER.writeImage(source, os, params);
            final ByteSource written = ByteSource.array(os.toByteArray());

            final ImageInfo info = PARSER.getImageInfo(written, null);
            assertEquals(expectedBpp * expectedPlanes, info.getBitsPerPixel(), "bits per pixel");
            final BufferedImage decoded = PARSER.getBufferedImage(written, null);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    assertEquals(source.getRGB(x, y) & 0xFFFFFF, decoded.getRGB(x, y) & 0xFFFFFF, "pixel " + x + "," + y + " compression " + compression);
                }
            }
        }
    }

    @Test
    void testWrite32BitHeaderAndFirstRow() throws IOException {
        // Only the first row is checked: PcxWriter.writePixels32 writes 4 * bytesPerLine bytes per row (see the coverage report),
        // so following rows are shifted when read back.
        final BufferedImage source = image(600, false);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        PARSER.writeImage(source, os, new PcxImagingParameters().setBitDepth(32));
        final ByteSource written = ByteSource.array(os.toByteArray());

        assertEquals(32, PARSER.getImageInfo(written, null).getBitsPerPixel());
        final BufferedImage decoded = PARSER.getBufferedImage(written, null);
        for (int x = 0; x < source.getWidth(); x++) {
            assertEquals(source.getRGB(x, 0) & 0xFFFFFF, decoded.getRGB(x, 0) & 0xFFFFFF);
        }
    }

    private static byte[] header(final int manufacturer, final int encoding, final int bpp, final int xMax, final int planes, final int bytesPerLine) {
        final ByteBuffer buf = ByteBuffer.allocate(128 + 64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) manufacturer).put((byte) 5).put((byte) encoding).put((byte) bpp);
        buf.putShort((short) 0).putShort((short) 0).putShort((short) xMax).putShort((short) 1);
        buf.putShort((short) 72).putShort((short) 72);
        buf.position(65);
        buf.put((byte) planes).putShort((short) bytesPerLine);
        return buf.array();
    }

    private static ImagingException readFails(final byte[] bytes) {
        return assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(ByteSource.array(bytes), null));
    }

    @Test
    void testInvalidHeaders() {
        assertTrue(readFails(header(11, 1, 8, 1, 1, 2)).getMessage().startsWith("Not a Valid PCX File: manufacturer"));
        assertTrue(readFails(header(10, 1, 8, 1, 5, 2)).getMessage().contains("5 planes"));
        assertTrue(readFails(header(10, 7, 8, 1, 1, 2)).getMessage().startsWith("Unsupported/invalid image encoding"));
        assertTrue(readFails(header(10, 1, 16, 1, 1, 4)).getMessage().startsWith("Invalid/unsupported image with bitsPerPixel"));
        final byte[] noPalette = java.util.Arrays.copyOf(header(10, 0, 8, 1, 1, 2), 128 + 4 + 769);
        assertEquals("No 256 color palette found in image that needs it", readFails(noPalette).getMessage());

        final ByteSource oddBytesPerLine = ByteSource.array(header(10, 1, 8, 1, 1, 3));
        assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(oddBytesPerLine, new PcxImagingParameters().setStrict(true)));
    }

    @Test
    void testNegativeSizes() {
        // xMin > xMax gives a negative width
        final byte[] bytes = header(10, 1, 8, 0, 1, 2);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(4, (short) 5);
        assertEquals("Image width is negative", readFails(bytes).getMessage());
        assertThrows(ImagingException.class, () -> PARSER.getImageSize(ByteSource.array(bytes), null));

        final byte[] negativeHeight = header(10, 1, 8, 1, 1, 2);
        ByteBuffer.wrap(negativeHeight).order(ByteOrder.LITTLE_ENDIAN).putShort(6, (short) 5);
        assertEquals("Image height is negative", readFails(negativeHeight).getMessage());
        assertThrows(ImagingException.class, () -> PARSER.getImageSize(ByteSource.array(negativeHeight), null));
    }

    @Test
    void testDumpAndAccessors() throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        PARSER.writeImage(image(4, false), os, null);
        final ByteSource written = ByteSource.array(os.toByteArray());

        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), written));
        assertTrue(sw.toString().contains("Manufacturer: 10"));
        assertTrue(sw.toString().contains("ColorMap: ("));
        assertEquals("pcx", PARSER.getDefaultExtension());
        assertNull(PARSER.getIccProfileBytes(written, null));
    }
}
