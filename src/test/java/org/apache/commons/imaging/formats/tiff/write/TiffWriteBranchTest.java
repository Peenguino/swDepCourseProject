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
package org.apache.commons.imaging.formats.tiff.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.TiffContents;
import org.apache.commons.imaging.formats.tiff.TiffDirectory;
import org.apache.commons.imaging.formats.tiff.TiffImageParser;
import org.apache.commons.imaging.formats.tiff.TiffImagingParameters;
import org.apache.commons.imaging.formats.tiff.TiffReader;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.fieldtypes.AbstractFieldType;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAsciiOrByte;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAsciiOrRational;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoByte;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoByteOrShort;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoBytes;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoDouble;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoDoubles;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoFloats;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoLongs;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoRationals;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSByte;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSBytes;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSLong;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSLongs;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSRationals;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSShort;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSShorts;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShortOrLong;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShortOrLongOrRational;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShortOrRational;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShorts;
import org.junit.jupiter.api.Test;

/**
 * Exercises the typed {@code add} overloads of {@link TiffOutputDirectory}, {@link TiffOutputSet} helpers, the directory validation of
 * {@link AbstractTiffImageWriter} and image writer parameters.
 */
class TiffWriteBranchTest {

    private static final TiffDirectoryType ROOT = TiffDirectoryType.TIFF_DIRECTORY_IFD0;
    private static final RationalNumber HALF = new RationalNumber(1, 2);

    private static TiffOutputDirectory directory() {
        return new TiffOutputDirectory(TiffDirectoryConstants.DIRECTORY_TYPE_ROOT, ByteOrder.BIG_ENDIAN);
    }

    private static void assertField(final TiffOutputDirectory dir, final int tag, final AbstractFieldType type, final int count) {
        final TiffOutputField field = dir.findField(tag);
        assertNotNull(field, "tag " + tag);
        assertSame(type, field.abstractFieldType);
        assertEquals(count, field.count);
    }

    @Test
    void testTypedAddOverloadsCheckValueCount() throws ImagingException {
        final TiffOutputDirectory dir = directory();
        int tag = 0xC000;

        final TagInfoAsciiOrByte asciiOrByte = new TagInfoAsciiOrByte("aob", tag++, 3, ROOT);
        dir.add(asciiOrByte, "ab");
        assertField(dir, asciiOrByte.tag, AbstractFieldType.ASCII, 3);
        assertThrows(ImagingException.class, () -> dir.add(asciiOrByte, "abcdef"));

        final TagInfoAsciiOrRational asciiOrRational = new TagInfoAsciiOrRational("aor", tag++, 2, ROOT);
        dir.add(asciiOrRational, HALF, HALF);
        // the count is the encoded byte length (2 rationals * 8 bytes), see the coverage report
        assertEquals(16, dir.findField(asciiOrRational.tag).getData().length);
        assertThrows(ImagingException.class, () -> dir.add(asciiOrRational, HALF));
        final TagInfoAsciiOrRational asciiOrRational2 = new TagInfoAsciiOrRational("aor2", tag++, 3, ROOT);
        dir.add(asciiOrRational2, "xy");
        assertField(dir, asciiOrRational2.tag, AbstractFieldType.ASCII, 3);
        assertThrows(ImagingException.class, () -> dir.add(asciiOrRational2, "toolong"));

        final TagInfoByte oneByte = new TagInfoByte("b", tag++, AbstractFieldType.BYTE, ROOT);
        dir.add(oneByte, (byte) 7);
        assertField(dir, oneByte.tag, AbstractFieldType.BYTE, 1);

        final TagInfoByteOrShort byteOrShort = new TagInfoByteOrShort("bos", tag++, 2, ROOT);
        dir.add(byteOrShort, (byte) 1, (byte) 2);
        assertField(dir, byteOrShort.tag, AbstractFieldType.BYTE, 2);
        assertThrows(ImagingException.class, () -> dir.add(byteOrShort, (byte) 1));
        final TagInfoByteOrShort byteOrShort2 = new TagInfoByteOrShort("bos2", tag++, 2, ROOT);
        dir.add(byteOrShort2, (short) 1, (short) 2);
        assertField(dir, byteOrShort2.tag, AbstractFieldType.SHORT, 2);
        assertThrows(ImagingException.class, () -> dir.add(byteOrShort2, (short) 1));

        final TagInfoBytes bytes = new TagInfoBytes("bs", tag++, AbstractFieldType.BYTE, 2, ROOT);
        assertThrows(ImagingException.class, () -> dir.add(bytes, (byte) 1));

        final TagInfoDouble oneDouble = new TagInfoDouble("d", tag++, ROOT);
        dir.add(oneDouble, 1.5);
        assertField(dir, oneDouble.tag, AbstractFieldType.DOUBLE, 1);
        final TagInfoDoubles doubles = new TagInfoDoubles("ds", tag++, 2, ROOT);
        assertThrows(ImagingException.class, () -> dir.add(doubles, 1.0));

        final TagInfoFloats floats = new TagInfoFloats("fs", tag++, 2, ROOT);
        dir.add(floats, 1f, 2f);
        assertField(dir, floats.tag, AbstractFieldType.FLOAT, 2);
        assertThrows(ImagingException.class, () -> dir.add(floats, 1f));

        final TagInfoLongs longs = new TagInfoLongs("ls", tag++, 2, ROOT);
        dir.add(longs, 1, 2);
        assertField(dir, longs.tag, AbstractFieldType.LONG, 2);
        assertThrows(ImagingException.class, () -> dir.add(longs, 1));

        final TagInfoRationals rationals = new TagInfoRationals("rs", tag++, 2, ROOT);
        assertThrows(ImagingException.class, () -> dir.add(rationals, HALF));

        final TagInfoSByte sbyte = new TagInfoSByte("sb", tag++, ROOT);
        dir.add(sbyte, (byte) -1);
        assertField(dir, sbyte.tag, AbstractFieldType.SBYTE, 1);
        final TagInfoSBytes sbytes = new TagInfoSBytes("sbs", tag++, 2, ROOT);
        dir.add(sbytes, (byte) -1, (byte) -2);
        assertField(dir, sbytes.tag, AbstractFieldType.SBYTE, 2);
        assertThrows(ImagingException.class, () -> dir.add(sbytes, (byte) 1));

        final TagInfoShortOrLong shortOrLong = new TagInfoShortOrLong("sol", tag++, 2, ROOT);
        dir.add(shortOrLong, 70000, 70001);
        assertField(dir, shortOrLong.tag, AbstractFieldType.LONG, 2);
        assertThrows(ImagingException.class, () -> dir.add(shortOrLong, 1));
        assertThrows(ImagingException.class, () -> dir.add(shortOrLong, (short) 1));

        final TagInfoShortOrLongOrRational solor = new TagInfoShortOrLongOrRational("solor", tag++, 2, ROOT);
        dir.add(solor, 1, 2);
        assertField(dir, solor.tag, AbstractFieldType.LONG, 2);
        assertThrows(ImagingException.class, () -> dir.add(solor, 1));
        final TagInfoShortOrLongOrRational solor2 = new TagInfoShortOrLongOrRational("solor2", tag++, 2, ROOT);
        dir.add(solor2, HALF, HALF);
        assertField(dir, solor2.tag, AbstractFieldType.RATIONAL, 2);
        assertThrows(ImagingException.class, () -> dir.add(solor2, HALF));
        final TagInfoShortOrLongOrRational solor3 = new TagInfoShortOrLongOrRational("solor3", tag++, 2, ROOT);
        dir.add(solor3, (short) 1, (short) 2);
        assertField(dir, solor3.tag, AbstractFieldType.SHORT, 2);
        assertThrows(ImagingException.class, () -> dir.add(solor3, (short) 1));

        final TagInfoShortOrRational sor = new TagInfoShortOrRational("sor", tag++, 2, ROOT);
        dir.add(sor, HALF, HALF);
        assertField(dir, sor.tag, AbstractFieldType.RATIONAL, 2);
        assertThrows(ImagingException.class, () -> dir.add(sor, HALF));
        final TagInfoShortOrRational sor2 = new TagInfoShortOrRational("sor2", tag++, 2, ROOT);
        dir.add(sor2, (short) 1, (short) 2);
        assertField(dir, sor2.tag, AbstractFieldType.SHORT, 2);
        assertThrows(ImagingException.class, () -> dir.add(sor2, (short) 1));

        final TagInfoShorts shorts = new TagInfoShorts("ss", tag++, 2, ROOT);
        assertThrows(ImagingException.class, () -> dir.add(shorts, (short) 1));

        final TagInfoSLong slong = new TagInfoSLong("sl", tag++, ROOT);
        dir.add(slong, -5);
        assertField(dir, slong.tag, AbstractFieldType.SLONG, 1);
        final TagInfoSLongs slongs = new TagInfoSLongs("sls", tag++, 2, ROOT);
        dir.add(slongs, -1, -2);
        assertField(dir, slongs.tag, AbstractFieldType.SLONG, 2);
        assertThrows(ImagingException.class, () -> dir.add(slongs, 1));

        final TagInfoSRationals srationals = new TagInfoSRationals("srs", tag++, 2, ROOT);
        dir.add(srationals, HALF, HALF);
        assertField(dir, srationals.tag, AbstractFieldType.SRATIONAL, 2);
        assertThrows(ImagingException.class, () -> dir.add(srationals, HALF));

        final TagInfoSShort sshort = new TagInfoSShort("sss", tag++, ROOT);
        dir.add(sshort, (short) -3);
        assertField(dir, sshort.tag, AbstractFieldType.SSHORT, 1);
        final TagInfoSShorts sshorts = new TagInfoSShorts("ssss", tag++, 2, ROOT);
        dir.add(sshorts, (short) -1, (short) -2);
        assertField(dir, sshorts.tag, AbstractFieldType.SSHORT, 2);
        assertThrows(ImagingException.class, () -> dir.add(sshorts, (short) 1));

        assertEquals("Root", dir.description());
        assertTrue(dir.getItemDescription().startsWith("Directory: "));
        assertNull(dir.getRawJpegImageData());
        assertNull(dir.getRawTiffImageData());
    }

    @Test
    void testOutputSetHelpers() throws ImagingException {
        final TiffOutputSet set = new TiffOutputSet();
        assertNull(set.getExifDirectory());
        assertNull(set.getGpsDirectory());
        assertNull(set.getInteroperabilityDirectory());

        final TiffOutputDirectory interop = set.addInteroperabilityDirectory();
        assertSame(interop, set.getInteroperabilityDirectory());
        assertNotNull(set.getExifDirectory(), "interoperability requires an EXIF directory");
        assertNotNull(set.getRootDirectory());
        assertSame(set.getRootDirectory(), set.getOrCreateRootDirectory());
        assertThrows(ImagingException.class, set::addInteroperabilityDirectory);

        set.setGpsInDegrees(-10.5, -20.25);
        assertSame(set.getGpsDirectory(), set.getOrCreateGpsDirectory());
        final TiffOutputField latRef = set.findField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
        assertEquals('S', (char) latRef.getData()[0]);

        set.getRootDirectory().add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, 10);
        set.removeField(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH);
        assertNull(set.findField(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH));
        set.getRootDirectory().add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, 10);
        set.removeField(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH.tag);
        assertNull(set.findField(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH.tag));

        final String text = set.toString();
        assertTrue(text.startsWith("TiffOutputSet {"));
        assertTrue(text.contains("directory 0"));
        assertTrue(text.contains("field"));
        set.dump();
    }

    private static ImagingException writeFails(final TiffOutputSet set) {
        return assertThrows(ImagingException.class, () -> new TiffImageWriterLossy().write(new ByteArrayOutputStream(), set));
    }

    @Test
    void testDirectoryValidation() throws ImagingException {
        assertEquals("No directories.", writeFails(new TiffOutputSet()).getMessage());

        final TiffOutputSet onlyExif = new TiffOutputSet();
        onlyExif.addDirectory(new TiffOutputDirectory(TiffDirectoryConstants.DIRECTORY_TYPE_EXIF, ByteOrder.BIG_ENDIAN));
        assertEquals("Missing root directory.", writeFails(onlyExif).getMessage());

        final TiffOutputSet gap = new TiffOutputSet();
        gap.addRootDirectory();
        gap.addDirectory(new TiffOutputDirectory(2, ByteOrder.BIG_ENDIAN));
        assertEquals("Missing directory: 1.", writeFails(gap).getMessage());

        final TiffOutputSet twice = new TiffOutputSet();
        final TiffOutputDirectory root = twice.addRootDirectory();
        root.add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, 1);
        root.add(new TiffOutputField(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, AbstractFieldType.LONG, 1, new byte[4]));
        assertTrue(writeFails(twice).getMessage().endsWith("appears twice in directory."));

        for (final org.apache.commons.imaging.formats.tiff.taginfos.TagInfo offset : new org.apache.commons.imaging.formats.tiff.taginfos.TagInfo[] {
                ExifTagConstants.EXIF_TAG_EXIF_OFFSET, ExifTagConstants.EXIF_TAG_INTEROP_OFFSET, ExifTagConstants.EXIF_TAG_GPSINFO }) {
            final TiffOutputSet dangling = new TiffOutputSet();
            dangling.addRootDirectory().add(new TiffOutputField(offset, AbstractFieldType.LONG, 1, new byte[4]));
            assertTrue(writeFails(dangling).getMessage().startsWith("Output set has"), offset.name);
        }
    }

    @Test
    void testFullOutputSetIsWrittenAndReadBack() throws IOException {
        final TiffOutputSet set = new TiffOutputSet(ByteOrder.LITTLE_ENDIAN);
        set.getOrCreateRootDirectory().add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, "hello");
        set.addInteroperabilityDirectory();
        set.setGpsInDegrees(1, 2);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new TiffImageWriterLossy(ByteOrder.LITTLE_ENDIAN).write(os, set);

        final TiffContents contents = new TiffReader(true).readContents(ByteSource.array(os.toByteArray()), new TiffImagingParameters(),
                org.apache.commons.imaging.FormatCompliance.getDefault());
        final java.util.Set<Integer> types = new java.util.HashSet<>();
        for (final TiffDirectory d : contents.directories) {
            types.add(d.type);
        }
        assertTrue(types.contains(TiffDirectoryConstants.DIRECTORY_TYPE_EXIF));
        assertTrue(types.contains(TiffDirectoryConstants.DIRECTORY_TYPE_GPS));
        assertTrue(types.contains(TiffDirectoryConstants.DIRECTORY_TYPE_INTEROPERABILITY));
        assertEquals("hello", contents.directories.get(0).getFieldValue(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION));
    }

    // ------------------------------------------------------------------ image writer parameters

    private static BufferedImage bilevel() {
        final BufferedImage image = new BufferedImage(16, 4, BufferedImage.TYPE_BYTE_BINARY);
        image.setRGB(3, 1, 0xFFFFFFFF);
        return image;
    }

    private static byte[] write(final BufferedImage image, final TiffImagingParameters params) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new TiffImageParser().writeImage(image, os, params);
        return os.toByteArray();
    }

    @Test
    void testWriterParameterValidation() {
        assertThrows(ImagingException.class, () -> write(bilevel(),
                new TiffImagingParameters().setCompression(TiffConstants.COMPRESSION_UNCOMPRESSED).setLzwCompressionBlockSize(100)));
        assertThrows(ImagingException.class,
                () -> write(bilevel(), new TiffImagingParameters().setCompression(TiffConstants.COMPRESSION_CCITT_GROUP_3).setT4Options(2)));
        assertThrows(ImagingException.class, () -> write(bilevel(), new TiffImagingParameters().setCompression(12345)));
    }

    @Test
    void testWriterOptionsAreStored() throws IOException {
        final TiffImageParser parser = new TiffImageParser();

        final TiffImagingParameters g4 = new TiffImagingParameters().setCompression(TiffConstants.COMPRESSION_CCITT_GROUP_4).setT6Options(0);
        g4.setXmpXml("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>");
        g4.setPixelDensity(PixelDensity.createUnitless(3, 4));
        final byte[] g4Bytes = write(bilevel(), g4);
        assertEquals("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>", parser.getXmpXml(ByteSource.array(g4Bytes), null));
        assertEquals(0xFFFFFFFF, parser.getBufferedImage(ByteSource.array(g4Bytes), null).getRGB(3, 1));

        final TiffImagingParameters cm = new TiffImagingParameters().setCompression(TiffConstants.COMPRESSION_LZW).setLzwCompressionBlockSize(8000);
        cm.setPixelDensity(PixelDensity.createFromPixelsPerCentimetre(10, 10));
        final BufferedImage rgb = new BufferedImage(5, 300, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(4, 299, 0x00FF00);
        final byte[] cmBytes = write(rgb, cm);
        assertEquals(0xFF00FF00, parser.getBufferedImage(ByteSource.array(cmBytes), null).getRGB(4, 299));
        final org.apache.commons.imaging.formats.tiff.TiffImageMetadata cmMetadata = (org.apache.commons.imaging.formats.tiff.TiffImageMetadata) parser
                .getMetadata(ByteSource.array(cmBytes));
        assertEquals(10.0, cmMetadata.findField(TiffTagConstants.TIFF_TAG_XRESOLUTION).getDoubleValue(), 1e-9);
    }

    @Test
    void testUserOutputSetIsMergedIntoWrittenImage() throws IOException {
        final TiffOutputSet user = new TiffOutputSet();
        user.getOrCreateRootDirectory().add(TiffTagConstants.TIFF_TAG_ARTIST, "someone");
        user.getOrCreateRootDirectory().add(TiffTagConstants.TIFF_TAG_IMAGE_WIDTH, 999); // ignored, the writer's value wins
        user.getOrCreateExifDirectory().add(ExifTagConstants.EXIF_TAG_USER_COMMENT, "comment");

        final byte[] bytes = write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), new TiffImagingParameters().setOutputSet(user));
        final TiffImageParser parser = new TiffImageParser();
        assertEquals(2, parser.getImageInfo(ByteSource.array(bytes), null).getWidth());
        final org.apache.commons.imaging.formats.tiff.TiffImageMetadata metadata = (org.apache.commons.imaging.formats.tiff.TiffImageMetadata) parser
                .getMetadata(ByteSource.array(bytes));
        assertEquals("someone", metadata.findField(TiffTagConstants.TIFF_TAG_ARTIST).getStringValue());
    }
}
