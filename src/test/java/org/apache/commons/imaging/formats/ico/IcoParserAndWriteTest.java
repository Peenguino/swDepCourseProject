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
package org.apache.commons.imaging.formats.ico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.test.TestResources;
import org.junit.jupiter.api.Test;

class IcoParserAndWriteTest {

    private static BufferedImage paletteImage(final int width, final int height, final int colors) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int index = (y * width + x) % colors;
                // Deterministic, well-separated palette entries.
                image.setRGB(x, y, index * 0x010203 | index * 0x000101);
            }
        }
        return image;
    }

    private static byte[] write(final BufferedImage src, final IcoImagingParameters params) throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new IcoImageParser().writeImage(src, os, params);
        return os.toByteArray();
    }

    private static BufferedImage read(final byte[] bytes) throws Exception {
        return new IcoImageParser().getBufferedImage(ByteSource.array(bytes), new IcoImagingParameters());
    }

    private static void assertRoundTrip(final BufferedImage src) throws Exception {
        final BufferedImage readBack = read(write(src, new IcoImagingParameters()));
        assertEquals(src.getWidth(), readBack.getWidth());
        assertEquals(src.getHeight(), readBack.getHeight());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                assertEquals(src.getRGB(x, y), readBack.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void testRoundTrip1Bit() throws Exception {
        final BufferedImage src = new BufferedImage(9, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                src.setRGB(x, y, (x + y) % 2 == 0 ? 0x000000 : 0xffffff);
            }
        }
        assertRoundTrip(src);
    }

    @Test
    void testRoundTrip4Bit() throws Exception {
        assertRoundTrip(paletteImage(8, 2, 16));
    }

    @Test
    void testRoundTrip8Bit() throws Exception {
        assertRoundTrip(paletteImage(9, 2, 17));
    }

    @Test
    void testRoundTrip24Bit() throws Exception {
        // More than 256 distinct colors: no exact palette, 24-bit output.
        assertRoundTrip(paletteImage(17, 17, 1000));
    }

    @Test
    void testRoundTrip32BitWithAlpha() throws Exception {
        final BufferedImage src = paletteImage(17, 17, 1000);
        final BufferedImage withAlpha = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 17; y++) {
            for (int x = 0; x < 17; x++) {
                final int alpha = (x + y) % 3 == 0 ? 0 : 0xff;
                withAlpha.setRGB(x, y, alpha << 24 | 0xffffff & src.getRGB(x, y));
            }
        }
        assertRoundTrip(withAlpha);
    }

    @Test
    void testRoundTripWideImageUsesZeroDimensionEntry() throws Exception {
        // Width > 255 must be stored as 0 in the icon directory entry.
        final BufferedImage src = paletteImage(300, 1, 2);
        final byte[] bytes = write(src, new IcoImagingParameters());
        assertEquals(0, bytes[6], "icon directory width must be 0 for images wider than 255 pixels");
        assertRoundTrip(src);
    }

    @Test
    void testWriteWithPixelDensity() throws Exception {
        final IcoImagingParameters params = new IcoImagingParameters();
        params.setPixelDensity(PixelDensity.createFromPixelsPerMetre(1000, 1000));
        final byte[] bytes = write(paletteImage(2, 2, 2), params);
        final BufferedImage readBack = read(bytes);
        assertEquals(2, readBack.getWidth());
    }

    @Test
    void testReadRealIconAndDump() throws Exception {
        final File file = TestResources.resourceToFile("/data/images/ico/1/Oregon Scientific DS6639 - DSC_0307 - small.ico");
        final IcoImageParser parser = new IcoImageParser();
        final List<BufferedImage> images = parser.getAllBufferedImages(file);
        assertTrue(images.size() >= 1);
        for (final BufferedImage image : images) {
            assertNotNull(image);
            assertTrue(image.getWidth() > 0);
            assertTrue(image.getHeight() > 0);
        }
        assertNotNull(parser.getBufferedImage(file, new IcoImagingParameters()));

        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            assertTrue(parser.dumpImageFile(pw, ByteSource.file(file)));
        }
        final String dump = out.toString();
        assertTrue(dump.contains("FileHeader"), dump);
        assertTrue(dump.contains("IconInfo"), dump);
        assertTrue(dump.contains("BitmapHeader"), dump);
        assertTrue(dump.contains("IconCount"), dump);
    }

    @Test
    void testGetBufferedImageWithNoIcons() {
        // Valid file header declaring zero icons.
        final byte[] bytes = { 0, 0, 1, 0, 0, 0 };
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("No icons"), e.getMessage());
    }

    @Test
    void testReadFileHeaderRejectsNonZeroReserved() {
        final byte[] bytes = { 1, 0, 1, 0, 1, 0 };
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("reserved"), e.getMessage());
    }

    @Test
    void testReadFileHeaderRejectsInvalidIconType() {
        final byte[] bytes = { 0, 0, 3, 0, 1, 0 };
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("icon type"), e.getMessage());
    }

    @Test
    void testReadBitmapIconDataRejectsWrongHeaderSize() throws Exception {
        final byte[] bytes = write(paletteImage(2, 2, 2), new IcoImagingParameters());
        bytes[22] = 41; // bitmap header size, little-endian int at offset 22
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("Wrong bitmap header size"), e.getMessage());
    }

    @Test
    void testReadBitmapIconDataRejectsInvalidPlanes() throws Exception {
        final byte[] bytes = write(paletteImage(2, 2, 2), new IcoImagingParameters());
        bytes[34] = 2; // planes, little-endian short at offset 22 + 12
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertTrue(e.getMessage().contains("Planes"), e.getMessage());
    }

    @Test
    void testReadPngEncodedIcon() throws Exception {
        // ICO entries may embed a complete PNG stream instead of a BMP bitmap.
        final BufferedImage src = paletteImage(4, 4, 16);
        final byte[] png = Imaging.writeImageToBytes(src, ImageFormats.PNG);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(new byte[] { 0, 0, 1, 0, 1, 0 }); // reserved, type = icon, count = 1
        os.write(new byte[] { 4, 4, 0, 0 }); // width, height, colors, reserved
        os.write(new byte[] { 1, 0, 32, 0 }); // planes, bit count
        os.write(new byte[] { (byte) png.length, (byte) (png.length >> 8), (byte) (png.length >> 16), (byte) (png.length >>> 24) });
        os.write(new byte[] { 22, 0, 0, 0 }); // image offset
        os.write(png);
        final byte[] bytes = os.toByteArray();

        final BufferedImage readBack = read(bytes);
        assertEquals(4, readBack.getWidth());
        assertEquals(src.getRGB(1, 1), readBack.getRGB(1, 1));

        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            assertTrue(new IcoImageParser().dumpImageFile(pw, ByteSource.array(bytes)));
        }
        assertTrue(out.toString().contains("PNGIconData"), out.toString());
    }

    @Test
    void testNameAndDefaults() {
        final IcoImageParser parser = new IcoImageParser();
        assertEquals("ico-Custom", parser.getName());
        assertEquals("ico", parser.getDefaultExtension());
        assertNotNull(parser.getDefaultParameters());
    }
}
