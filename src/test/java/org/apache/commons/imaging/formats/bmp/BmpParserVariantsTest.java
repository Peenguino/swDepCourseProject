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
package org.apache.commons.imaging.formats.bmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.imaging.FormatCompliance;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

class BmpParserVariantsTest {

    private static final int BI_RGB = 0;
    private static final int BI_RLE8 = 1;
    private static final int BI_RLE4 = 2;
    private static final int BI_BITFIELDS = 3;

    /** Two palette entries: index 0 = black, index 1 = white (B, G, R, reserved). */
    private static final byte[] PALETTE = { 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0 };

    private static byte[] bmp(final int id1, final int id2, final int width, final int height, final int bpp, final int compression, final int colorsUsed,
            final byte[] palette, final int dataOffset, final byte[] extra, final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.allocate(dataOffset + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) id1).put((byte) id2);
        buffer.putInt(dataOffset + data.length); // file size
        buffer.putInt(0); // reserved
        buffer.putInt(dataOffset);
        buffer.putInt(40); // info header size
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putShort((short) 1); // planes
        buffer.putShort((short) bpp);
        buffer.putInt(compression);
        buffer.putInt(data.length);
        buffer.putInt(0); // horizontal resolution
        buffer.putInt(0); // vertical resolution
        buffer.putInt(colorsUsed);
        buffer.putInt(0); // important colors
        buffer.put(palette);
        buffer.put(extra);
        buffer.put(data);
        return buffer.array();
    }

    private static byte[] rle(final int width, final int height, final int bpp, final int compression, final byte[] data) {
        return bmp('B', 'M', width, height, bpp, compression, 2, PALETTE, 54 + PALETTE.length, new byte[0], data);
    }

    private static BufferedImage read(final byte[] bytes) throws Exception {
        return new BmpImageParser().getBufferedImage(ByteSource.array(bytes), new BmpImagingParameters());
    }

    @Test
    void testRle8EncodedMode() throws Exception {
        // Bottom row: two white pixels then EOL; top row: three black pixels then EOB.
        final BufferedImage image = read(rle(3, 2, 8, BI_RLE8, new byte[] { 2, 1, 0, 0, 3, 0, 0, 1 }));
        assertEquals(0xff000000, image.getRGB(0, 0));
        assertEquals(0xff000000, image.getRGB(2, 0));
        assertEquals(0xffffffff, image.getRGB(0, 1));
        assertEquals(0xffffffff, image.getRGB(1, 1));
    }

    @Test
    void testRle8AbsoluteMode() throws Exception {
        final BufferedImage image = read(rle(3, 1, 8, BI_RLE8, new byte[] { 0, 3, 1, 0, 1, 0, 0, 1 }));
        assertEquals(0xffffffff, image.getRGB(0, 0));
        assertEquals(0xff000000, image.getRGB(1, 0));
        assertEquals(0xffffffff, image.getRGB(2, 0));
    }

    @Test
    void testRle8DeltaMode() throws Exception {
        // Delta of one pixel in x, then a single white pixel, then EOB.
        final BufferedImage image = read(rle(3, 1, 8, BI_RLE8, new byte[] { 0, 2, 1, 0, 1, 1, 0, 1 }));
        assertEquals(0xffffffff, image.getRGB(1, 0));
    }

    @Test
    void testRle4EncodedMode() throws Exception {
        // Four pixels alternating white/black via a single 0x10 byte.
        final BufferedImage image = read(rle(4, 1, 4, BI_RLE4, new byte[] { 4, 0x10, 0, 1 }));
        assertEquals(0xffffffff, image.getRGB(0, 0));
        assertEquals(0xff000000, image.getRGB(1, 0));
        assertEquals(0xffffffff, image.getRGB(2, 0));
        assertEquals(0xff000000, image.getRGB(3, 0));
    }

    @Test
    void testRle4AbsoluteModeOddCount() throws Exception {
        // Three samples: white, black from 0x10, then black from the high nibble of 0x00.
        final BufferedImage image = read(rle(4, 1, 4, BI_RLE4, new byte[] { 0, 3, 0x10, 0, 0, 1 }));
        assertEquals(0xffffffff, image.getRGB(0, 0));
        assertEquals(0xff000000, image.getRGB(1, 0));
        assertEquals(0xff000000, image.getRGB(2, 0));
    }

    @Test
    void testRle4AbsoluteModeEvenCount() throws Exception {
        // Four samples stored in exactly two bytes (no padding byte needed).
        final BufferedImage image = read(rle(4, 1, 4, BI_RLE4, new byte[] { 0, 4, 0x11, 0x11, 0, 1 }));
        assertEquals(0xffffffff, image.getRGB(0, 0));
        assertEquals(0xffffffff, image.getRGB(1, 0));
        assertEquals(0xffffffff, image.getRGB(2, 0));
        assertEquals(0xffffffff, image.getRGB(3, 0));
    }

    @Test
    void testRleRejectsUnsupportedBitsPerPixel() {
        final byte[] bytes = rle(1, 1, 16, BI_RLE8, new byte[] { 1, 0, 0, 1 });
        assertThrows(ImagingException.class, () -> read(bytes));
    }

    @Test
    void testUnknownCompression() {
        final byte[] bytes = bmp('B', 'M', 1, 1, 24, 99, 0, new byte[0], 54, new byte[0], new byte[] { 0, 0, 0, 0 });
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("Unknown Compression"), e.getMessage());
    }

    @Test
    void testInvalidDataOffset() {
        // Valid header with a data offset pointing inside the header itself.
        final byte[] bytes = bmp('B', 'M', 1, 1, 24, BI_RGB, 0, new byte[0], 54, new byte[0], new byte[] { 0, 0, 0, 0 });
        bytes[10] = 10; // bitmap data offset, little-endian int at offset 10
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("invalid image data offset"), e.getMessage());
    }

    @Test
    void testExtraBytesBeforeImageDataAreSkipped() throws Exception {
        final byte[] bytes = bmp('B', 'M', 1, 1, 24, BI_RGB, 0, new byte[0], 56, new byte[] { 9, 9 }, new byte[] { 0, 0, (byte) 0xff, 0 });
        assertEquals(0xffff0000, read(bytes).getRGB(0, 0));
    }

    @Test
    void testBitFields32Bit() throws Exception {
        // BI_BITFIELDS with a 40-byte header stores the three masks right after it.
        final ByteBuffer buffer = ByteBuffer.allocate(66 + 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'B').put((byte) 'M');
        buffer.putInt(70); // file size
        buffer.putInt(0);
        buffer.putInt(66); // data offset
        buffer.putInt(40);
        buffer.putInt(1); // width
        buffer.putInt(1); // height
        buffer.putShort((short) 1);
        buffer.putShort((short) 32);
        buffer.putInt(BI_BITFIELDS);
        buffer.putInt(4);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0x00ff0000); // red mask
        buffer.putInt(0x0000ff00); // green mask
        buffer.putInt(0x000000ff); // blue mask
        buffer.put(new byte[] { 0, 0, (byte) 0xff, 0 });

        assertEquals(0xff0000, read(buffer.array()).getRGB(0, 0) & 0xffffff);
    }

    @Test
    void testBmpTypeDescriptions() throws Exception {
        final Object[][] cases = {
                { 'B', 'A', "OS/2 Bitmap Array" },
                { 'C', 'I', "OS/2 Color Icon" },
                { 'C', 'P', "OS/2 Color Pointer" },
                { 'I', 'C', "OS/2 Icon" },
                { 'P', 'T', "OS/2 Pointer" },
                { 'X', 'X', "Unknown" },
        };
        for (final Object[] c : cases) {
            final byte[] bytes = bmp((char) c[0], (char) c[1], 1, 1, 24, BI_RGB, 0, new byte[0], 54, new byte[0], new byte[] { 0, 0, 0, 0 });
            final ImageInfo info = new BmpImageParser().getImageInfo(ByteSource.array(bytes), new BmpImagingParameters());
            assertTrue(info.getFormatDetails().contains((String) c[2]), info.getFormatDetails());
        }
    }

    @Test
    void testWriteWithPixelDensityAndFormatCompliance() throws Exception {
        final BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xff0000);
        final BmpImagingParameters params = new BmpImagingParameters();
        params.setPixelDensity(PixelDensity.createFromPixelsPerMetre(1000, 2000));

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new BmpImageParser().writeImage(src, os, params);
        final byte[] bytes = os.toByteArray();

        final Logger logger = Logger.getLogger(BmpImageParser.class.getName());
        final Level previous = logger.getLevel();
        logger.setLevel(Level.FINE);
        try {
            assertEquals(0xffff0000, read(bytes).getRGB(0, 0));
        } finally {
            logger.setLevel(previous);
        }

        final FormatCompliance compliance = new BmpImageParser().getFormatCompliance(ByteSource.array(bytes));
        assertNotNull(compliance);
        assertEquals("Bmp-Custom", new BmpImageParser().getName());
    }
}
