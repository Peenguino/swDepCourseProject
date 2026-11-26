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
package org.apache.commons.imaging.formats.png;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.ImageMetadata;
import org.junit.jupiter.api.Test;

/**
 * Builds PNG streams chunk by chunk to reach the interlaced, gamma, low bit depth and malformed-file branches of the PNG reader, plus writer options.
 */
class PngParserBranchTest {

    private static final PngImageParser PARSER = new PngImageParser();
    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    // ---------------------------------------------------------------- helpers

    private static byte[] chunk(final String type, final byte[] data) {
        final byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        final CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue()).array();
    }

    private static byte[] ihdr(final int width, final int height, final int bitDepth, final int colorType, final int filter, final int interlace) {
        return chunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) bitDepth).put((byte) colorType).put((byte) 0)
                .put((byte) filter).put((byte) interlace).array());
    }

    private static byte[] idat(final byte[] rawScanlines) {
        final Deflater deflater = new Deflater();
        deflater.setInput(rawScanlines);
        deflater.finish();
        final byte[] buf = new byte[rawScanlines.length + 64];
        final int len = deflater.deflate(buf);
        deflater.end();
        final byte[] data = new byte[len];
        System.arraycopy(buf, 0, data, 0, len);
        return chunk("IDAT", data);
    }

    private static byte[] gama(final int gammaTimes100000) {
        return chunk("gAMA", ByteBuffer.allocate(4).putInt(gammaTimes100000).array());
    }

    private static byte[] iend() {
        return chunk("IEND", new byte[0]);
    }

    private static byte[] png(final byte[]... chunks) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SIGNATURE, 0, SIGNATURE.length);
        for (final byte[] c : chunks) {
            out.write(c, 0, c.length);
        }
        return out.toByteArray();
    }

    /** 1x1 RGB pixel (filter none). */
    private static byte[] rgbPixel(final int r, final int g, final int b) {
        return idat(new byte[] { 0, (byte) r, (byte) g, (byte) b });
    }

    private static BufferedImage read(final byte[] bytes) throws IOException {
        return PARSER.getBufferedImage(ByteSource.array(bytes), null);
    }

    private static byte[] writeInterlacedWithImageIo(final BufferedImage image) throws IOException {
        final ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        final ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- reading

    @Test
    void testInterlacedRgbImage() throws IOException {
        final BufferedImage source = new BufferedImage(9, 7, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, x * 25 << 16 | y * 30 << 8 | (x + y) * 10);
            }
        }
        final byte[] bytes = writeInterlacedWithImageIo(source);

        assertTrue(PARSER.getImageInfo(ByteSource.array(bytes), null).isProgressive());
        final BufferedImage decoded = read(bytes);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                assertEquals(source.getRGB(x, y), decoded.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void testInterlacedOneBitImage() throws IOException {
        final BufferedImage source = new BufferedImage(10, 3, BufferedImage.TYPE_BYTE_BINARY);
        source.setRGB(1, 0, 0xFFFFFFFF);
        source.setRGB(9, 2, 0xFFFFFFFF);

        final BufferedImage decoded = read(writeInterlacedWithImageIo(source));

        assertEquals(0xFFFFFFFF, decoded.getRGB(1, 0));
        assertEquals(0xFFFFFFFF, decoded.getRGB(9, 2));
        assertEquals(0xFF000000, decoded.getRGB(0, 0));
    }

    @Test
    void testTwoBitGrayscaleScalesSamples() throws IOException {
        // 4 pixels of 2 bits: 0, 1, 2, 3 -> 0, 85, 170, 255
        final byte[] bytes = png(ihdr(4, 1, 2, 0, 0, 0), idat(new byte[] { 0, 0b00011011 }), iend());
        final BufferedImage image = read(bytes);
        assertEquals(0xFF000000, image.getRGB(0, 0));
        assertEquals(0xFF555555, image.getRGB(1, 0));
        assertEquals(0xFFAAAAAA, image.getRGB(2, 0));
        assertEquals(0xFFFFFFFF, image.getRGB(3, 0));
    }

    @Test
    void testGammaCorrectionAppliedToRgbGrayAndPalette() throws IOException {
        final int gamma = 45455; // 1/2.2, far from the 1.0 target -> correction applied
        final int rgb = read(png(ihdr(1, 1, 8, 2, 0, 0), gama(gamma), rgbPixel(128, 64, 32), iend())).getRGB(0, 0);
        assertNotEquals(0xFF804020, rgb);
        assertEquals(0xFF, rgb >>> 24);

        final int gray = read(png(ihdr(1, 1, 8, 0, 0, 0), gama(gamma), idat(new byte[] { 0, (byte) 128 }), iend())).getRGB(0, 0);
        assertNotEquals(0xFF808080, gray);

        final int grayAlpha = read(png(ihdr(1, 1, 8, 4, 0, 0), gama(gamma), idat(new byte[] { 0, (byte) 128, (byte) 255 }), iend())).getRGB(0, 0);
        assertEquals(gray, grayAlpha);

        final byte[] plte = chunk("PLTE", new byte[] { (byte) 128, 64, 32 });
        final int indexed = read(png(ihdr(1, 1, 8, 3, 0, 0), gama(gamma), plte, idat(new byte[] { 0, 0 }), iend())).getRGB(0, 0);
        assertEquals(rgb, indexed);

        // gamma close to the target: no correction
        assertEquals(0xFF804020, read(png(ihdr(1, 1, 8, 2, 0, 0), gama(100000), rgbPixel(128, 64, 32), iend())).getRGB(0, 0));
    }

    @Test
    void testMalformedChunkStructures() {
        final byte[] ihdr = ihdr(1, 1, 8, 2, 0, 0);
        final byte[] data = rgbPixel(1, 2, 3);
        final byte[] plte = chunk("PLTE", new byte[] { 1, 2, 3 });
        final byte[] srgb = chunk("sRGB", new byte[] { 0 });
        final byte[] phys = chunk("pHYs", new byte[9]);

        assertMessage("PNG contains more than one Header", png(ihdr, ihdr, data, iend()));
        assertMessage("PNG contains more than one Palette", png(ihdr, plte, plte, data, iend()));
        assertMessage("PNG missing image data", png(ihdr, iend()));
        assertMessage("PNG: unexpected sRGB chunk", png(ihdr, srgb, srgb, data, iend()));
        assertMessage("PNG: unexpected gAMA chunk", png(ihdr, gama(45455), gama(45455), data, iend()));
        assertMessage("PNG: unknown FilterMethod: 1", png(ihdr(1, 1, 8, 2, 1, 0), data, iend()));
        assertMessage("PNG: unknown interlace method: 2", png(ihdr(1, 1, 8, 2, 0, 2), data, iend()));
        assertMessage("PNG: unknown filterType: 9", png(ihdr, idat(new byte[] { 9, 1, 2, 3 }), iend()));
        // a signature with no chunk at all is reported as a truncated stream
        assertThrows(IOException.class, () -> read(png()));

        final ImagingException twoPhys = assertThrows(ImagingException.class,
                () -> PARSER.getImageInfo(ByteSource.array(png(ihdr, phys, phys, data, iend())), null));
        assertTrue(twoPhys.getMessage().startsWith("PNG contains more than one pHYs"));
    }

    private static void assertMessage(final String expected, final byte[] bytes) {
        final ImagingException e = assertThrows(ImagingException.class, () -> read(bytes));
        assertEquals(expected, e.getMessage());
    }

    // ---------------------------------------------------------------- metadata helpers

    @Test
    void testChunkInspectionHelpers() throws IOException {
        final byte[] bytes = png(ihdr(1, 1, 8, 2, 0, 0), chunk("tEXt", "Title\0Hello".getBytes(StandardCharsets.ISO_8859_1)), rgbPixel(1, 2, 3), iend());
        final ByteSource source = ByteSource.array(bytes);

        final ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        PARSER.readSignature(is);
        final List<String> types = PARSER.getChunkTypes(is);
        assertEquals(java.util.Arrays.asList("IHDR", "tEXt", "IDAT", "IEND"), types);

        assertTrue(PARSER.hasChunkType(source, ChunkType.tEXt));
        assertFalse(PARSER.hasChunkType(source, ChunkType.iCCP));
        assertNull(PARSER.getExifMetadata(source, null));
        assertNull(PARSER.getIccProfileBytes(source, null));
        assertNull(PARSER.getXmpXml(source, null));
        assertEquals("png", PARSER.getDefaultExtension());

        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), source));
        assertTrue(sw.toString().contains("chunks: 4"));

        final ImageMetadata metadata = PARSER.getMetadata(source, null);
        assertTrue(metadata.toString().contains("Title: Hello"));
        assertEquals(1, metadata.getItems().size());
    }

    // ---------------------------------------------------------------- writing

    @Test
    void testWriterOptionsRoundTrip() throws IOException {
        final BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80FF0000);
        image.setRGB(1, 1, 0xFF00FF00);
        final String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>";

        final PngImagingParameters params = new PngImagingParameters();
        params.setForceTrueColor(true);
        params.setBitDepth((byte) 8);
        params.setXmpXml(xmp);
        params.setPixelDensity(PixelDensity.createFromPixelsPerInch(72, 96));
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        PARSER.writeImage(image, os, params);
        final ByteSource written = ByteSource.array(os.toByteArray());

        assertEquals(xmp, PARSER.getXmpXml(written, null));
        final ImageInfo info = PARSER.getImageInfo(written, null);
        assertEquals(72, info.getPhysicalWidthDpi());
        assertEquals(96, info.getPhysicalHeightDpi());
        assertEquals(0x80FF0000, read(os.toByteArray()).getRGB(0, 0));

        final PngImagingParameters unitless = new PngImagingParameters();
        unitless.setPixelDensity(PixelDensity.createUnitless(2, 3));
        final ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        PARSER.writeImage(image, os2, unitless);
        assertTrue(PARSER.hasChunkType(ByteSource.array(os2.toByteArray()), ChunkType.pHYs));
    }

    @Test
    void testWriterRejectsInvalidOptions() {
        final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        final PngImagingParameters both = new PngImagingParameters().setForceIndexedColor(true).setForceTrueColor(true);
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(), both));

        final String notLatin1 = "€";
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Text(notLatin1, "x")))));
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Text("k", notLatin1)))));
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Ztxt(notLatin1, "x")))));
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Ztxt("k", notLatin1)))));
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Itxt(notLatin1, "x", "en", "k")))));
        assertThrows(ImagingException.class, () -> PARSER.writeImage(image, new ByteArrayOutputStream(),
                new PngImagingParameters().setTextChunks(Collections.singletonList(new AbstractPngText.Itxt("k", "x", notLatin1, "k")))));
    }
}
