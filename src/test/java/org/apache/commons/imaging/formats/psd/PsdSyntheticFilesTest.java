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
package org.apache.commons.imaging.formats.psd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

/**
 * Builds minimal PSD files for every supported color mode and both compressions, plus image resources (XMP, ICC) and error cases.
 */
class PsdSyntheticFilesTest {

    private static final PsdImageParser PARSER = new PsdImageParser();
    private static final int WIDTH = 8;
    private static final int HEIGHT = 2;

    /** Minimal PSD writer for tests. */
    private static final class Psd {
        int channels = 1;
        int depth = 8;
        int mode = 1;
        int compression;
        byte[] colorModeData = new byte[0];
        byte[] resources = new byte[0];
        /** channel-major samples, one byte per sample (or packed bits for depth 1). */
        byte[][] planes = { new byte[WIDTH * HEIGHT] };

        byte[] build() {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ByteBuffer header = ByteBuffer.allocate(26);
            header.put("8BPS".getBytes(StandardCharsets.US_ASCII)).putShort((short) 1).put(new byte[6]).putShort((short) channels).putInt(HEIGHT)
                    .putInt(WIDTH).putShort((short) depth).putShort((short) mode);
            out.write(header.array(), 0, 26);
            writeSection(out, colorModeData);
            writeSection(out, resources);
            writeSection(out, new byte[0]); // layer and mask
            out.write(compression >> 8);
            out.write(compression);
            if (compression == 1) {
                final int rowBytes = (WIDTH * depth + 7) / 8;
                for (int c = 0; c < channels; c++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        out.write(0);
                        out.write(rowBytes + 1);
                    }
                }
                for (final byte[] plane : planes) {
                    for (int y = 0; y < HEIGHT; y++) {
                        out.write(rowBytes - 1); // PackBits literal run
                        out.write(plane, y * rowBytes, rowBytes);
                    }
                }
            } else {
                for (final byte[] plane : planes) {
                    out.write(plane, 0, plane.length);
                }
            }
            return out.toByteArray();
        }

        private static void writeSection(final ByteArrayOutputStream out, final byte[] data) {
            out.write(ByteBuffer.allocate(4).putInt(data.length).array(), 0, 4);
            out.write(data, 0, data.length);
        }
    }

    private static byte[] resource(final int id, final byte[] data) {
        final int padded = data.length + data.length % 2;
        // empty Pascal name (1 length byte + 1 pad byte)
        return ByteBuffer.allocate(4 + 2 + 2 + 4 + padded).put("8BIM".getBytes(StandardCharsets.US_ASCII)).putShort((short) id).put((byte) 0).put((byte) 0)
                .putInt(data.length).put(data).array();
    }

    private static byte[] concat(final byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    private static byte[] filled(final int value) {
        final byte[] plane = new byte[WIDTH * HEIGHT];
        java.util.Arrays.fill(plane, (byte) value);
        return plane;
    }

    private static BufferedImage read(final Psd psd) throws IOException {
        return PARSER.getBufferedImage(ByteSource.array(psd.build()), null);
    }

    @Test
    void testGrayscaleAndDuotoneBothCompressions() throws IOException {
        for (final int mode : new int[] { 1, 8 }) {
            for (final int compression : new int[] { 0, 1 }) {
                final Psd psd = new Psd();
                psd.mode = mode;
                psd.compression = compression;
                psd.planes[0][3] = (byte) 0x80;
                final BufferedImage image = read(psd);
                assertEquals(WIDTH, image.getWidth());
                assertEquals(0xFF808080, image.getRGB(3, 0), "mode " + mode + " compression " + compression);
                assertEquals(0xFF000000, image.getRGB(0, 0));
            }
        }
    }

    @Test
    void testRgbCmykAndLab() throws IOException {
        final Psd rgb = new Psd();
        rgb.mode = 3;
        rgb.channels = 3;
        rgb.planes = new byte[][] { filled(0x11), filled(0x22), filled(0x33) };
        assertEquals(0xFF112233, read(rgb).getRGB(1, 1));

        final Psd cmyk = new Psd();
        cmyk.mode = 4;
        cmyk.channels = 4;
        cmyk.planes = new byte[][] { filled(0xFF), filled(0xFF), filled(0xFF), filled(0xFF) };
        // PSD stores inverted CMYK: all 0xFF means no ink -> white
        assertEquals(0xFFFFFFFF, read(cmyk).getRGB(0, 0));

        final Psd lab = new Psd();
        lab.mode = 9;
        lab.channels = 3;
        lab.planes = new byte[][] { filled(0xFF), filled(0x80), filled(0x80) };
        final int labRgb = read(lab).getRGB(0, 0);
        assertTrue((labRgb >> 16 & 0xFF) > 0xF0, "L=100 is close to white: " + Integer.toHexString(labRgb));
    }

    @Test
    void testBitmapAndIndexed() throws IOException {
        final Psd bitmap = new Psd();
        bitmap.mode = 0;
        bitmap.depth = 1;
        bitmap.planes = new byte[][] { new byte[] { (byte) 0x80, 0 } };
        final BufferedImage bw = read(bitmap);
        assertEquals(0xFF000000, bw.getRGB(0, 0), "set bit is black");
        assertEquals(0xFFFFFFFF, bw.getRGB(1, 0), "clear bit is white");

        final Psd indexed = new Psd();
        indexed.mode = 2;
        indexed.colorModeData = new byte[768];
        indexed.colorModeData[5] = 0x10; // red of entry 5
        indexed.colorModeData[256 + 5] = 0x20;
        indexed.colorModeData[512 + 5] = 0x30;
        indexed.planes = new byte[][] { filled(5) };
        final ByteSource source = ByteSource.array(indexed.build());
        assertEquals(0xFF102030, PARSER.getBufferedImage(source, null).getRGB(0, 0));
        assertTrue(PARSER.getImageInfo(source, null).usesPalette());
    }

    @Test
    void testUnsupportedModeAndCompression() {
        final Psd multichannel = new Psd();
        multichannel.mode = 7;
        assertEquals("Unknown Mode: 7", assertThrows(ImagingException.class, () -> read(multichannel)).getMessage());

        final Psd zip = new Psd();
        zip.compression = 2;
        assertEquals("Unknown Compression: 2", assertThrows(ImagingException.class, () -> read(zip)).getMessage());
        final ImageInfo info = assertDoesNotThrowInfo(zip);
        assertEquals(ImageInfo.CompressionAlgorithm.UNKNOWN, info.getCompressionAlgorithm());

        final byte[] notPsd = "8BPX".getBytes(StandardCharsets.US_ASCII);
        assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(ByteSource.array(notPsd), null));
    }

    private static ImageInfo assertDoesNotThrowInfo(final Psd psd) {
        try {
            return PARSER.getImageInfo(ByteSource.array(psd.build()), null);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void testImageInfoForEachMode() throws IOException {
        final int[][] modeChannels = { { 0, 1 }, { 1, 1 }, { 3, 3 }, { 4, 4 }, { 7, 2 }, { 8, 1 }, { 9, 3 }, { 15, 1 } };
        for (final int[] mc : modeChannels) {
            final Psd psd = new Psd();
            psd.mode = mc[0];
            psd.channels = mc[1];
            final ImageInfo info = PARSER.getImageInfo(ByteSource.array(psd.build()), null);
            assertEquals(WIDTH, info.getWidth());
            assertEquals(ImageInfo.CompressionAlgorithm.NONE, info.getCompressionAlgorithm());
            assertTrue(info.getBitsPerPixel() >= 0, "mode " + mc[0]);
        }
    }

    @Test
    void testResourcesXmpAndIcc() throws IOException {
        final String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>";
        final Psd psd = new Psd();
        psd.resources = concat(resource(0x03ED, new byte[] { 1, 2, 3 }), resource(PsdImageParser.IMAGE_RESOURCE_ID_XMP, xmp.getBytes(StandardCharsets.UTF_8)),
                resource(PsdImageParser.IMAGE_RESOURCE_ID_ICC_PROFILE, new byte[] { 9, 8 }));
        final ByteSource source = ByteSource.array(psd.build());

        assertEquals(xmp, PARSER.getXmpXml(source, null));
        assertArrayEquals(new byte[] { 9, 8 }, PARSER.getIccProfileBytes(source, null));
        assertEquals(new Dimension(WIDTH, HEIGHT), PARSER.getImageSize(source, null));
        assertNotNull(PARSER.getBufferedImage(source, null));

        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), source));
        assertTrue(sw.toString().contains("blocks.size(): "));
        assertTrue(sw.toString().contains("Mode: 1"));
        assertTrue(sw.toString().contains("Compression: 0"));
    }

    @Test
    void testMissingAndDuplicateResources() throws IOException {
        final Psd none = new Psd();
        final ByteSource empty = ByteSource.array(none.build());
        assertNull(PARSER.getXmpXml(empty, null));
        assertNull(PARSER.getIccProfileBytes(empty, null));

        final Psd emptyIcc = new Psd();
        emptyIcc.resources = resource(PsdImageParser.IMAGE_RESOURCE_ID_ICC_PROFILE, new byte[0]);
        assertNull(PARSER.getIccProfileBytes(ByteSource.array(emptyIcc.build()), null));

        final Psd otherOnly = new Psd();
        otherOnly.resources = resource(0x03ED, new byte[] { 1 });
        assertNull(PARSER.getXmpXml(ByteSource.array(otherOnly.build()), null));

        final Psd twoXmp = new Psd();
        twoXmp.resources = concat(resource(PsdImageParser.IMAGE_RESOURCE_ID_XMP, new byte[] { 'a' }), resource(PsdImageParser.IMAGE_RESOURCE_ID_XMP, new byte[] { 'b' }));
        assertThrows(ImagingException.class, () -> PARSER.getXmpXml(ByteSource.array(twoXmp.build()), null));
    }

    @Test
    void testSectionAccessorsReturnTheRightBytes() throws Exception {
        // getData / getInputStream are private helpers only called with fixed sections by the parser; check every section offset.
        final java.lang.reflect.Method getData = PsdImageParser.class.getDeclaredMethod("getData", ByteSource.class, int.class);
        final java.lang.reflect.Method getInputStream = PsdImageParser.class.getDeclaredMethod("getInputStream", ByteSource.class, int.class);
        getData.setAccessible(true);
        getInputStream.setAccessible(true);

        final Psd psd = new Psd();
        psd.colorModeData = new byte[] { 7, 7 };
        psd.resources = resource(0x03ED, new byte[] { 1, 2 });
        psd.planes[0][0] = 42;
        final ByteSource source = ByteSource.array(psd.build());

        final byte[] header = (byte[]) getData.invoke(PARSER, source, 0);
        assertEquals("8BPS", new String(header, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(26, header.length);
        assertArrayEquals(new byte[] { 7, 7 }, (byte[]) getData.invoke(PARSER, source, 1));
        assertArrayEquals(psd.resources, (byte[]) getData.invoke(PARSER, source, 2));
        assertEquals(0, ((byte[]) getData.invoke(PARSER, source, 3)).length);
        for (final int section : new int[] { 4, 99 }) {
            final java.lang.reflect.InvocationTargetException e = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> getData.invoke(PARSER, source, section));
            assertTrue(e.getCause() instanceof ImagingException);
        }

        try (java.io.InputStream is = (java.io.InputStream) getInputStream.invoke(PARSER, source, 0)) {
            assertEquals('8', is.read());
        }
        try (java.io.InputStream is = (java.io.InputStream) getInputStream.invoke(PARSER, source, 1)) {
            assertEquals(7, is.read(), "positioned at the color mode data");
        }
        try (java.io.InputStream is = (java.io.InputStream) getInputStream.invoke(PARSER, source, 3)) {
            assertEquals(0, is.read(), "positioned after the layer section length, at the compression field");
        }
        try (java.io.InputStream is = (java.io.InputStream) getInputStream.invoke(PARSER, source, 4)) {
            assertEquals(42, is.read(), "positioned at the first image sample");
        }
        final java.lang.reflect.InvocationTargetException unknown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> getInputStream.invoke(PARSER, source, 99));
        assertEquals("getInputStream: Unknown Section: 99", unknown.getCause().getMessage());
    }

    @Test
    void testResourceWithOddLengthName() throws IOException {
        // name length 1 -> length byte + 1 char is already even, so no padding byte follows
        final byte[] named = ByteBuffer.allocate(4 + 2 + 2 + 4 + 2).put("8BIM".getBytes(StandardCharsets.US_ASCII))
                .putShort((short) PsdImageParser.IMAGE_RESOURCE_ID_ICC_PROFILE).put((byte) 1).put((byte) 'N').putInt(2).put((byte) 5).put((byte) 6).array();
        final Psd psd = new Psd();
        psd.resources = named;
        assertArrayEquals(new byte[] { 5, 6 }, PARSER.getIccProfileBytes(ByteSource.array(psd.build()), null));
    }

    @Test
    void testAccessorsAndValueObjects() {
        assertEquals("psd", PARSER.getDefaultExtension());
        assertNotNull(PARSER.getDefaultParameters());

        final PsdHeaderInfo header = new PsdHeaderInfo(1, new byte[] { 1, 2, 3, 4, 5, 6 }, 3, 2, 8, 8, 3);
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6 }, header.getReserved());
        final StringWriter headerDump = new StringWriter();
        header.dump(new PrintWriter(headerDump));
        assertTrue(headerDump.toString().contains("Columns: 8"));
        header.dump();

        final PsdImageContents contents = new PsdImageContents(header, 0, 10, 0, 1);
        final StringWriter contentsDump = new StringWriter();
        contents.dump(new PrintWriter(contentsDump));
        assertTrue(contentsDump.toString().contains("ImageResourcesLength: 10"));
        contents.dump();
    }
}
