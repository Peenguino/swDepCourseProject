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
package org.apache.commons.imaging.formats.icns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Builds ICNS containers by hand to exercise writer sizes, 4 bit images, ARGB fall-backs and the error branches.
 */
class IcnsParserBranchTest {

    private static final IcnsImageParser PARSER = new IcnsImageParser();

    private static byte[] element(final String type, final byte[] data) {
        return ByteBuffer.allocate(8 + data.length).put(type.getBytes(StandardCharsets.US_ASCII)).putInt(8 + data.length).put(data).array();
    }

    private static ByteSource icns(final byte[]... elements) {
        int size = 8;
        for (final byte[] e : elements) {
            size += e.length;
        }
        final ByteBuffer buf = ByteBuffer.allocate(size).put("icns".getBytes(StandardCharsets.US_ASCII)).putInt(size);
        for (final byte[] e : elements) {
            buf.put(e);
        }
        return ByteSource.array(buf.array());
    }

    @ParameterizedTest
    @ValueSource(ints = { 16, 32, 48, 128 })
    void testWriteAndReadBackSupportedSizes(final int size) throws IOException {
        final BufferedImage source = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF0000);
        source.setRGB(size - 1, size - 1, 0x800000FF);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        PARSER.writeImage(source, os, null);
        final ByteSource written = ByteSource.array(os.toByteArray());

        assertEquals(new Dimension(size, size), PARSER.getImageSize(written, null));
        final BufferedImage decoded = PARSER.getBufferedImage(written, null);
        assertEquals(0xFFFF0000, decoded.getRGB(0, 0));
        assertEquals(0x80, decoded.getRGB(size - 1, size - 1) >>> 24);
        assertEquals(1, PARSER.getAllBufferedImages(written).size());
    }

    @Test
    void testWriteUnsupportedSizeFails() {
        assertThrows(ImagingException.class, () -> PARSER.writeImage(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), new ByteArrayOutputStream(), null));
    }

    @Test
    void testFourBitImageUsesPalette() throws IOException {
        final byte[] data = new byte[16 * 16 / 2];
        data[0] = 0x0F; // first pixel palette index 0 (white), second index 15 (black)
        final BufferedImage image = PARSER.getBufferedImage(icns(element("ics4", data)), null);

        assertEquals(16, image.getWidth());
        assertEquals(0xFFFFFFFF, image.getRGB(0, 0));
        assertEquals(0xFF000000, image.getRGB(1, 0));
    }

    @Test
    void testUndecodableArgbElementsFallBackToBlankImages() throws IOException {
        // "icp4" (16x16) and "ic07" (128x128) normally hold PNG/JPEG2000 data; garbage gives an empty image of the right size
        final BufferedImage small = PARSER.getBufferedImage(icns(element("icp4", new byte[] { 1, 2, 3 })), null);
        assertEquals(16, small.getWidth());
        assertEquals(0, small.getRGB(0, 0));

        final BufferedImage large = PARSER.getBufferedImage(icns(element("ic07", new byte[] { 1, 2, 3 })), null);
        assertEquals(128, large.getHeight());
    }

    @Test
    void testShortNon32BitImageDataIsRejected() {
        final ImagingException e = assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(icns(element("ics8", new byte[10])), null));
        assertEquals("Short image data but not a 32 bit compressed type", e.getMessage());
    }

    @Test
    void testFileWithoutIconsIsRejected() {
        final ByteSource maskOnly = icns(element("s8mk", new byte[256]));
        assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(maskOnly, null));
        assertThrows(ImagingException.class, () -> PARSER.getImageInfo(maskOnly, null));
        assertThrows(ImagingException.class, () -> PARSER.getImageSize(maskOnly, null));
    }

    @Test
    void testCorruptedContainers() {
        final ByteSource badMagic = ByteSource.array(ByteBuffer.allocate(8).put("icnX".getBytes(StandardCharsets.US_ASCII)).putInt(8).array());
        assertTrue(assertThrows(ImagingException.class, () -> PARSER.getBufferedImage(badMagic, null)).getMessage().startsWith("Not a Valid ICNS File"));

        final byte[] oversized = ByteBuffer.allocate(16).put("ics8".getBytes(StandardCharsets.US_ASCII)).putInt(1000).putInt(0).putInt(0).array();
        final IOException e = assertThrows(IOException.class, () -> PARSER.getBufferedImage(icns(oversized), null));
        assertTrue(e.getMessage().startsWith("Corrupted ICNS file"));
    }

    @Test
    void testDumpAndSimpleAccessors() throws IOException {
        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), icns(element("ics4", new byte[128]), element("zzzz", new byte[2]))));
        final String dump = sw.toString();
        assertTrue(dump.contains("IcnsHeader"));
        assertTrue(dump.contains("(ics4)"));
        assertTrue(dump.contains("(zzzz)"));

        assertEquals("icns", PARSER.getDefaultExtension());
        assertEquals("Apple Icon Image", PARSER.getName());
        assertNotNull(PARSER.getDefaultParameters());
        assertNull(PARSER.getMetadata(icns(element("ics4", new byte[128])), null));
        assertNull(PARSER.getIccProfileBytes(icns(element("ics4", new byte[128])), null));
    }

    @Test
    void testIcnsTypeLookups() {
        assertSame(IcnsType.ICNS_16x16_8BIT_MASK, IcnsType.findAnyType(IcnsType.typeAsInt("s8mk")));
        assertSame(IcnsType.ICNS_16x16_4BIT_IMAGE, IcnsType.findAnyType(IcnsType.typeAsInt("ics4")));
        assertNull(IcnsType.findAnyType(IcnsType.typeAsInt("zzzz")));
        assertNull(IcnsType.find1BPPMaskType(IcnsType.ICNS_128x128_32BIT_IMAGE));
        assertNull(IcnsType.find8BPPMaskType(IcnsType.ICNS_16x12_4BIT_IMAGE));
        assertEquals("it32", IcnsType.describeType(IcnsType.ICNS_128x128_32BIT_IMAGE.getType()));
        assertThrows(IllegalArgumentException.class, () -> IcnsType.typeAsInt("abc"));
        assertTrue(IcnsType.ICNS_32x32_4BIT_IMAGE.toString().contains("width=32"));
        assertNotEquals(IcnsType.ICNS_32x32_4BIT_IMAGE.getType(), IcnsType.ICNS_32x32_8BIT_IMAGE.getType());
    }
}
