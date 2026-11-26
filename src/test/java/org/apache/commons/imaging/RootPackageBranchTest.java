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
package org.apache.commons.imaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImagingOpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.png.PngImageParser;
import org.apache.commons.imaging.formats.psd.PsdImageParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Covers the remaining branches of the root package: format guessing, the byte/file overloads of {@link Imaging} and {@link AbstractImageParser},
 * {@link ColorTools}, {@link PixelDensity}, {@link FormatCompliance} and {@link ImageDump}.
 */
class RootPackageBranchTest {

    @TempDir
    Path tempDir;

    private static byte[] png() throws IOException {
        final BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0x123456);
        return Imaging.writeImageToBytes(image, ImageFormats.PNG);
    }

    private static byte[] hex(final String hex) {
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    // ------------------------------------------------------------------ guessFormat

    @ParameterizedTest
    @CsvSource({ "5031, PBM", "5034, PBM", "5032, PGM", "5035, PGM", "5033, PPM", "5036, PPM", "5037, PAM", "974A4232, JBIG2", "6963, ICNS",
            "B168, DCX", "233F, RGBE", "52494646000000005745425000, WEBP", "52494646000000004156492000, UNKNOWN", "5249585800, UNKNOWN",
            "974A0000, UNKNOWN", "0102, UNKNOWN" })
    void testGuessFormatFromMagicNumbers(final String magic, final String expected) throws IOException {
        assertEquals(expected, Imaging.guessFormat(hex(magic)).getName());
    }

    @Test
    void testGuessFormatFallsBackToExtensionAndRejectsShortInput() throws IOException {
        assertSame(ImageFormats.PCX, Imaging.guessFormat(ByteSource.array(new byte[] { 1, 2, 3 }, "picture.PCX")));
        assertSame(ImageFormats.UNKNOWN, Imaging.guessFormat((ByteSource) null));
        for (final String truncated : new String[] { "01", "974A", "974A42", "5249", "52494646", "524946460000" }) {
            assertThrows(IllegalArgumentException.class, () -> Imaging.guessFormat(hex(truncated)), truncated);
        }
    }

    @Test
    void testHasImageFileExtension() throws IOException {
        assertFalse(Imaging.hasImageFileExtension((File) null));
        assertFalse(Imaging.hasImageFileExtension(tempDir.toFile()), "a directory is not an image file");
        assertFalse(Imaging.hasImageFileExtension((String) null));
        final Path png = Files.write(tempDir.resolve("a.png"), png());
        assertTrue(Imaging.hasImageFileExtension(png.toFile()));
    }

    // ------------------------------------------------------------------ Imaging overloads

    @Test
    void testImagingByteAndStreamOverloads() throws IOException {
        final byte[] png = png();

        assertTrue(Imaging.dumpImageFile(png).contains("Format Name: PNG"));
        assertNull(Imaging.getFormatCompliance(png), "PNG has no compliance report");
        assertNull(Imaging.getIccProfile(png));
        assertEquals(1, Imaging.getAllBufferedImages(png).size());
        assertEquals(1, Imaging.getAllBufferedImages(new ByteArrayInputStream(png), "a.png").size());
        assertEquals(0x123456, Imaging.getBufferedImage(new ByteArrayInputStream(png)).getRGB(1, 1) & 0xFFFFFF);
        assertEquals(3, Imaging.getImageInfo("a.png", png).getWidth());
        assertNull(Imaging.getXmpXml(png));
        assertNull(Imaging.getXmpXml(new ByteArrayInputStream(png), "a.png"));

        final Path file = Files.write(tempDir.resolve("b.png"), png);
        assertTrue(Imaging.dumpImageFile(file.toFile()).contains("PNG"));
        assertNull(Imaging.getFormatCompliance(file.toFile()));
    }

    // ------------------------------------------------------------------ AbstractImageParser

    @Test
    void testAbstractParserOverloadsAndExtensionFilter() throws IOException {
        final PngImageParser parser = new PngImageParser();
        final byte[] png = png();
        final File wrongExtension = Files.write(tempDir.resolve("image.gif"), png).toFile();
        final File noExtension = Files.write(tempDir.resolve("image"), png).toFile();
        final File right = Files.write(tempDir.resolve("image.png"), png).toFile();

        assertFalse(parser.canAcceptExtension(noExtension));
        assertNull(parser.dumpImageFile(wrongExtension));
        assertNull(parser.getAllBufferedImages(wrongExtension));
        assertNull(parser.getBufferedImage(wrongExtension, null));
        assertNull(parser.getFormatCompliance(wrongExtension));
        assertNull(parser.getIccProfileBytes(wrongExtension));
        assertNull(parser.getImageInfo(wrongExtension, null));
        assertNull(parser.getImageSize(wrongExtension));
        assertNull(parser.getMetadata(wrongExtension));

        assertTrue(parser.dumpImageFile(png).contains("chunks"));
        assertEquals(1, parser.getAllBufferedImages(png).size());
        assertNull(parser.getFormatCompliance(png));
        assertNull(parser.getFormatCompliance(right));
        assertNull(parser.getIccProfileBytes(png));
        assertNull(parser.getIccProfileBytes(right));
        assertEquals(3, parser.getImageSize(png).width);
        assertEquals(2, parser.getImageSize(right).height);
        assertNull(parser.getMetadata(ByteSource.array(png)), "no textual or EXIF chunks");

        // RGBE does not override the default PrintWriter dump and the PSD parser cannot write
        assertFalse(new org.apache.commons.imaging.formats.rgbe.RgbeImageParser().dumpImageFile(new PrintWriter(new StringWriter()), ByteSource.array(png)));
        final ImagingException e = assertThrows(ImagingException.class,
                () -> new PsdImageParser().writeImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), new ByteArrayOutputStream(), null));
        assertTrue(e.getMessage().contains("cannot be written"));

        final ImagingParameters<?> params = new ImagingParameters<>().setFileName("x.png");
        assertEquals("x.png", params.getFileName());
    }

    // ------------------------------------------------------------------ ColorTools

    @Test
    void testColorToolsConversions() throws IOException {
        final ColorTools tools = new ColorTools();
        final ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        final ColorSpace linear = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        final ICC_Profile srgbProfile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        final ICC_Profile linearProfile = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);

        final BufferedImage direct = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        direct.setRGB(0, 0, 0x808080);

        final BufferedImage toLinear = tools.convertBetweenColorSpaces(direct, srgb, linear);
        assertSame(linear, toLinear.getColorModel().getColorSpace());
        final BufferedImage back = tools.convertBetweenColorSpaces(toLinear, linear, srgb);
        assertEquals(0x80, back.getRGB(0, 0) & 0xFF, 2.0);

        assertSame(linear, tools.convertBetweenColorSpacesX2(direct, srgb, linear).getColorModel().getColorSpace());
        assertTrue(tools.convertBetweenIccProfiles(direct, srgbProfile, linearProfile).getColorModel().getColorSpace() instanceof ICC_ColorSpace);
        assertSame(linear, tools.convertToColorSpace(direct, linear).getColorModel().getColorSpace());
        assertNotNull(tools.convertToIccProfile(direct, linearProfile));
        assertNotNull(tools.convertTosRgb(tools.relabelColorSpace(direct, linearProfile)));

        final BufferedImage component = new BufferedImage(2, 1, BufferedImage.TYPE_3BYTE_BGR);
        final ColorModel opaque = tools.deriveColorModel(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR), linear, true);
        assertTrue(opaque instanceof ComponentColorModel);
        assertFalse(opaque.hasAlpha());
        assertTrue(tools.deriveColorModel(component, linear) instanceof ComponentColorModel);
        assertTrue(tools.deriveColorModel(direct, linear, false) instanceof DirectColorModel);
        assertThrows(ImagingOpException.class, () -> tools.deriveColorModel(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED), linear));

        // a PNG without ICC profile is returned unchanged
        final File file = Files.write(tempDir.resolve("c.png"), png()).toFile();
        assertSame(direct, tools.correctImage(direct, file));
    }

    // ------------------------------------------------------------------ PixelDensity / FormatCompliance

    @Test
    void testPixelDensityUnitConversions() {
        final PixelDensity cm = PixelDensity.createFromPixelsPerCentimetre(10, 20);
        assertTrue(cm.isInCentimetres());
        assertFalse(cm.isInInches());
        assertEquals(10, cm.horizontalDensityCentimetres(), 1e-9);
        assertEquals(20, cm.verticalDensityCentimetres(), 1e-9);
        assertEquals(25.4, cm.horizontalDensityInches(), 1e-9);
        assertEquals(50.8, cm.verticalDensityInches(), 1e-9);

        final PixelDensity m = PixelDensity.createFromPixelsPerMetre(1000, 2000);
        assertFalse(m.isInCentimetres());
        assertEquals(10, m.horizontalDensityCentimetres(), 1e-9);
        assertEquals(20, m.verticalDensityCentimetres(), 1e-9);
    }

    @Test
    void testFormatCompliance() throws ImagingException {
        final FormatCompliance lenient = new FormatCompliance("lenient");
        assertTrue(lenient.toString().contains("No comments."));
        assertFalse(lenient.checkBounds("v", 0, 10, 11));
        assertFalse(lenient.compareBytes("b", new byte[] { 1 }, new byte[] { 1, 2 }));
        final String report = lenient.toString();
        assertTrue(report.contains("Format Compliance: lenient"));
        assertTrue(report.contains("1: v: bounds check"));
        assertTrue(report.contains("2: b: Unexpected length"));
        lenient.dump();

        final FormatCompliance strict = new FormatCompliance("strict", true);
        assertEquals("boom", assertThrows(ImagingException.class, () -> strict.addComment("boom")).getMessage());
    }

    // ------------------------------------------------------------------ ImageDump

    /** Non-ICC color space whose type is chosen by the test. */
    private static final class FakeColorSpace extends ColorSpace {
        private static final long serialVersionUID = 1L;

        FakeColorSpace(final int type) {
            super(type, 3);
        }

        @Override
        public float[] fromCIEXYZ(final float[] colorvalue) {
            return colorvalue;
        }

        @Override
        public float[] fromRGB(final float[] rgbvalue) {
            return rgbvalue;
        }

        @Override
        public float[] toCIEXYZ(final float[] colorvalue) {
            return colorvalue;
        }

        @Override
        public float[] toRGB(final float[] colorvalue) {
            return colorvalue;
        }
    }

    @Test
    void testImageDumpLogsColorSpaceAndProperties() throws IOException {
        final Logger logger = Logger.getLogger(ImageDump.class.getName());
        final List<String> messages = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void close() {
                // nothing
            }

            @Override
            public void flush() {
                // nothing
            }

            @Override
            public void publish(final LogRecord record) {
                messages.add(record.getMessage());
            }
        };
        final Level previous = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(handler);
        try {
            final ImageDump dump = new ImageDump();
            final Hashtable<String, Object> props = new Hashtable<>();
            props.put("author", "me");
            final BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            final BufferedImage withProps = new BufferedImage(source.getColorModel(), source.getRaster(), false, props);
            dump.dump("img", withProps);
            assertTrue(messages.contains("img: author: me"));
            assertTrue(messages.contains("img: type: " + ColorSpace.TYPE_RGB + " (TYPE_RGB)"));

            final int[][] typeNames = { { ColorSpace.TYPE_CMYK }, { ColorSpace.CS_sRGB }, { ColorSpace.CS_GRAY }, { ColorSpace.CS_CIEXYZ },
                    { ColorSpace.CS_LINEAR_RGB }, { ColorSpace.CS_PYCC }, { ColorSpace.TYPE_HLS } };
            final String[] names = { "TYPE_CMYK", "CS_sRGB", "CS_GRAY", "CS_CIEXYZ", "CS_LINEAR_RGB", "CS_PYCC", "unknown" };
            for (int i = 0; i < names.length; i++) {
                messages.clear();
                dump.dumpColorSpace("cs", new FakeColorSpace(typeNames[i][0]));
                assertEquals("cs: type: " + typeNames[i][0] + " (" + names[i] + ")", messages.get(0));
                assertTrue(messages.get(1).startsWith("cs: Unknown ColorSpace: "));
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
        assertEquals("text", new String("text".getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII));
    }
}
