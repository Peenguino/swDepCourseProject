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
package org.apache.commons.imaging.formats.tiff;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import org.apache.commons.imaging.FormatCompliance;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.fieldtypes.AbstractFieldType;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoByte;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoBytes;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoDouble;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoDoubles;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoFloat;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoFloats;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoGpsText;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoLong;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoLongs;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoRational;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoRationals;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSByte;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSBytes;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSLong;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSLongs;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSRational;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSRationals;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSShort;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoSShorts;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShort;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShortOrLong;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoShorts;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoXpString;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Writes one field of every TIFF type and reads them back through every typed {@code getFieldValue} overload of {@link TiffDirectory} and
 * {@link TiffImageMetadata}, including the missing, wrong-type and wrong-count branches, plus GPS information.
 */
class TiffFieldAccessTest {

    private static final TiffDirectoryType ROOT = TiffDirectoryType.TIFF_DIRECTORY_IFD0;
    private static final RationalNumber HALF = new RationalNumber(1, 2);
    private static final RationalNumber MINUS_HALF = new RationalNumber(-1, 2);

    private static final TagInfoAscii ASCII = new TagInfoAscii("ascii", 0xC101, -1, ROOT);
    private static final TagInfoByte BYTE = new TagInfoByte("byte", 0xC102, AbstractFieldType.BYTE, ROOT);
    private static final TagInfoBytes BYTES = new TagInfoBytes("bytes", 0xC103, AbstractFieldType.BYTE, 2, ROOT);
    private static final TagInfoDouble DOUBLE = new TagInfoDouble("double", 0xC104, ROOT);
    private static final TagInfoDoubles DOUBLES = new TagInfoDoubles("doubles", 0xC105, 2, ROOT);
    private static final TagInfoFloat FLOAT = new TagInfoFloat("float", 0xC106, ROOT);
    private static final TagInfoFloats FLOATS = new TagInfoFloats("floats", 0xC107, 2, ROOT);
    private static final TagInfoLong LONG = new TagInfoLong("long", 0xC108, ROOT);
    private static final TagInfoLongs LONGS = new TagInfoLongs("longs", 0xC109, 2, ROOT);
    private static final TagInfoRational RATIONAL = new TagInfoRational("rational", 0xC10A, ROOT);
    private static final TagInfoRationals RATIONALS = new TagInfoRationals("rationals", 0xC10B, 2, ROOT);
    private static final TagInfoSByte SBYTE = new TagInfoSByte("sbyte", 0xC10C, ROOT);
    private static final TagInfoSBytes SBYTES = new TagInfoSBytes("sbytes", 0xC10D, 2, ROOT);
    private static final TagInfoShort SHORT = new TagInfoShort("short", 0xC10E, ROOT);
    private static final TagInfoShorts SHORTS = new TagInfoShorts("shorts", 0xC10F, 2, ROOT);
    private static final TagInfoSLong SLONG = new TagInfoSLong("slong", 0xC110, ROOT);
    private static final TagInfoSLongs SLONGS = new TagInfoSLongs("slongs", 0xC111, 2, ROOT);
    private static final TagInfoSRational SRATIONAL = new TagInfoSRational("srational", 0xC112, ROOT);
    private static final TagInfoSRationals SRATIONALS = new TagInfoSRationals("srationals", 0xC113, 2, ROOT);
    private static final TagInfoSShort SSHORT = new TagInfoSShort("sshort", 0xC114, ROOT);
    private static final TagInfoSShorts SSHORTS = new TagInfoSShorts("sshorts", 0xC115, 2, ROOT);
    private static final TagInfoGpsText GPS_TEXT = new TagInfoGpsText("gpstext", 0xC116, ROOT);
    private static final TagInfoXpString XP = new TagInfoXpString("xp", 0xC117, ROOT);

    /** Tag number with no field at all. */
    private static final int MISSING = 0xC1FF;

    private static TiffDirectory directory;
    private static TiffImageMetadata metadata;

    @BeforeAll
    static void writeAndRead() throws IOException {
        final TiffOutputSet set = new TiffOutputSet(ByteOrder.LITTLE_ENDIAN);
        final TiffOutputDirectory out = set.getOrCreateRootDirectory();
        out.add(ASCII, "hello");
        out.add(BYTE, (byte) 7);
        out.add(BYTES, (byte) 1, (byte) 2);
        out.add(DOUBLE, 1.5);
        out.add(DOUBLES, 1.0, 2.0);
        out.add(FLOAT, 2.5f);
        out.add(FLOATS, 3f, 4f);
        out.add(LONG, 70000);
        out.add(LONGS, 1, 2);
        out.add(RATIONAL, HALF);
        out.add(RATIONALS, HALF, HALF);
        out.add(SBYTE, (byte) -3);
        out.add(SBYTES, (byte) -1, (byte) -2);
        out.add(SHORT, (short) 9);
        out.add(SHORTS, (short) 5, (short) 6);
        out.add(SLONG, -70000);
        out.add(SLONGS, -1, -2);
        out.add(SRATIONAL, MINUS_HALF);
        out.add(SRATIONALS, MINUS_HALF, HALF);
        out.add(SSHORT, (short) -9);
        out.add(SSHORTS, (short) -5, (short) -6);
        out.add(GPS_TEXT, "gps");
        out.add(XP, "xp text");

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new TiffImageWriterLossy(ByteOrder.LITTLE_ENDIAN).write(os, set);
        final TiffContents contents = new TiffReader(true).readContents(ByteSource.array(os.toByteArray()), new TiffImagingParameters(),
                FormatCompliance.getDefault());
        directory = contents.directories.get(0);
        metadata = (TiffImageMetadata) new TiffImageParser().getMetadata(ByteSource.array(os.toByteArray()), null);
    }

    /** Asserts that reading a field through a mismatching tag info fails. */
    private static void assertFails(final Executable executable) {
        assertThrows(ImagingException.class, executable);
    }

    @Test
    void testSingleValueGetters() throws ImagingException {
        assertEquals(7, directory.getFieldValue(BYTE));
        assertEquals(1.5, directory.getFieldValue(DOUBLE));
        assertEquals(2.5f, directory.getFieldValue(FLOAT));
        assertEquals(70000, directory.getFieldValue(LONG));
        assertEquals(0.5, directory.getFieldValue(RATIONAL).doubleValue());
        assertEquals(-3, directory.getFieldValue(SBYTE));
        assertEquals(9, directory.getFieldValue(SHORT));
        assertEquals(-70000, directory.getFieldValue(SLONG));
        assertEquals(-0.5, directory.getFieldValue(SRATIONAL).doubleValue());
        assertEquals(-9, directory.getFieldValue(SSHORT));

        // missing
        assertFails(() -> directory.getFieldValue(new TagInfoByte("x", MISSING, AbstractFieldType.BYTE, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoDouble("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoFloat("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoLong("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoRational("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSByte("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoShort("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSLong("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSRational("x", MISSING, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSShort("x", MISSING, ROOT)));

        // wrong type: read the ASCII field with every numeric accessor
        assertFails(() -> directory.getFieldValue(new TagInfoByte("x", ASCII.tag, AbstractFieldType.BYTE, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoDouble("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoFloat("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoLong("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoRational("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSByte("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoShort("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSLong("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSRational("x", ASCII.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSShort("x", ASCII.tag, ROOT)));

        // wrong count: single accessors on the two-valued fields
        assertFails(() -> directory.getFieldValue(new TagInfoByte("x", BYTES.tag, AbstractFieldType.BYTE, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoDouble("x", DOUBLES.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoFloat("x", FLOATS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoLong("x", LONGS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoRational("x", RATIONALS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSByte("x", SBYTES.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoShort("x", SHORTS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSLong("x", SLONGS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSRational("x", SRATIONALS.tag, ROOT)));
        assertFails(() -> directory.getFieldValue(new TagInfoSShort("x", SSHORTS.tag, ROOT)));
    }

    @Test
    void testArrayGetters() throws ImagingException {
        assertArrayEquals(new String[] { "hello" }, directory.getFieldValue(ASCII, true));
        assertArrayEquals(new byte[] { 1, 2 }, directory.getFieldValue(BYTES, true));
        assertArrayEquals(new double[] { 1.0, 2.0 }, directory.getFieldValue(DOUBLES, true));
        assertArrayEquals(new float[] { 3f, 4f }, directory.getFieldValue(FLOATS, true));
        assertArrayEquals(new int[] { 1, 2 }, directory.getFieldValue(LONGS, true));
        assertEquals(2, directory.getFieldValue(RATIONALS, true).length);
        assertArrayEquals(new byte[] { -1, -2 }, directory.getFieldValue(SBYTES, true));
        assertArrayEquals(new short[] { 5, 6 }, directory.getFieldValue(SHORTS, true));
        assertArrayEquals(new int[] { -1, -2 }, directory.getFieldValue(SLONGS, true));
        assertEquals(-0.5, directory.getFieldValue(SRATIONALS, true)[0].doubleValue());
        assertArrayEquals(new short[] { -5, -6 }, directory.getFieldValue(SSHORTS, true));
        assertEquals("gps", directory.getFieldValue(GPS_TEXT, true));
        assertEquals("xp text", directory.getFieldValue(XP, true));
        assertArrayEquals(new int[] { 5, 6 }, directory.getFieldValue(new TagInfoShortOrLong("x", SHORTS.tag, 2, ROOT), true));
        assertArrayEquals(new int[] { 1, 2 }, directory.getFieldValue(new TagInfoShortOrLong("x", LONGS.tag, 2, ROOT), true));
        assertEquals("hello", directory.getSingleFieldValue(ASCII));
        assertEquals(70000, directory.getSingleFieldValue(new TagInfoShortOrLong("x", LONG.tag, 1, ROOT)));
    }

    private interface ArrayGetter {
        Object get(int tag, boolean mustExist) throws ImagingException;
    }

    private static void assertMissingAndWrongType(final ArrayGetter getter, final int wrongTypeTag) throws ImagingException {
        assertNull(getter.get(MISSING, false));
        assertFails(() -> getter.get(MISSING, true));
        assertNull(getter.get(wrongTypeTag, false));
        assertFails(() -> getter.get(wrongTypeTag, true));
    }

    @Test
    void testArrayGettersMissingAndWrongType() throws ImagingException {
        final int text = ASCII.tag;
        final int number = LONG.tag;
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoAscii("x", t, -1, ROOT), m), number);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoBytes("x", t, AbstractFieldType.BYTE, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoDoubles("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoFloats("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoLongs("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoRationals("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoSBytes("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoShorts("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoShortOrLong("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoSLongs("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoSRationals("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoSShorts("x", t, -1, ROOT), m), text);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoGpsText("x", t, ROOT), m), number);
        assertMissingAndWrongType((t, m) -> directory.getFieldValue(new TagInfoXpString("x", t, ROOT), m), text);
    }

    @Test
    void testMetadataGetters() throws ImagingException {
        assertArrayEquals(new String[] { "hello" }, metadata.getFieldValue(ASCII));
        assertArrayEquals(new byte[] { 7 }, metadata.getFieldValue(BYTE));
        assertArrayEquals(new double[] { 1.0, 2.0 }, metadata.getFieldValue(DOUBLES));
        assertArrayEquals(new float[] { 3f, 4f }, metadata.getFieldValue(FLOATS));
        assertEquals("gps", metadata.getFieldValue(GPS_TEXT));
        assertArrayEquals(new int[] { 1, 2 }, metadata.getFieldValue(LONGS));
        assertEquals(2, metadata.getFieldValue(RATIONALS).length);
        assertArrayEquals(new byte[] { -1, -2 }, metadata.getFieldValue(SBYTES));
        assertArrayEquals(new short[] { 5, 6 }, metadata.getFieldValue(SHORTS));
        assertArrayEquals(new int[] { -1, -2 }, metadata.getFieldValue(SLONGS));
        assertEquals(2, metadata.getFieldValue(SRATIONALS).length);
        assertArrayEquals(new short[] { -5, -6 }, metadata.getFieldValue(SSHORTS));
        assertEquals("xp text", metadata.getFieldValue(XP));
        assertEquals(70000, ((Number) metadata.getFieldValue((TagInfo) LONG)).intValue());

        // missing tags
        assertNull(metadata.getFieldValue(new TagInfoAscii("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoByte("x", MISSING, AbstractFieldType.BYTE, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoDoubles("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoFloats("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoGpsText("x", MISSING, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoLongs("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoRationals("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSBytes("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoShorts("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSLongs("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSRationals("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSShorts("x", MISSING, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoXpString("x", MISSING, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfo("x", MISSING, AbstractFieldType.LONG)));

        // wrong types
        final int text = ASCII.tag;
        assertNull(metadata.getFieldValue(new TagInfoAscii("x", LONG.tag, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoByte("x", text, AbstractFieldType.BYTE, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoDoubles("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoFloats("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoLongs("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoRationals("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSBytes("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoShorts("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSLongs("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSRationals("x", text, -1, ROOT)));
        assertNull(metadata.getFieldValue(new TagInfoSShorts("x", text, -1, ROOT)));
    }

    @Test
    void testFindFieldAcrossDirectories() throws ImagingException {
        // a tag declared for another directory is still found through the fall-back searches
        final TagInfoLong inExif = new TagInfoLong("x", LONG.tag, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD);
        assertNotNull(metadata.findField(inExif));
        assertNull(metadata.findField(inExif, true), "exact directory match fails");
        final TagInfoLong unknownDir = new TagInfoLong("x", LONG.tag, TiffDirectoryType.EXIF_DIRECTORY_UNKNOWN);
        assertNotNull(metadata.findField(unknownDir));
        assertNull(metadata.findField(new TagInfoLong("x", MISSING, TiffDirectoryType.EXIF_DIRECTORY_UNKNOWN)));
        assertNull(metadata.getGpsInfo(), "no GPS directory");
        assertTrue(metadata.toString().contains("hello"));
    }

    // ------------------------------------------------------------------ GPS

    private static TiffImageMetadata gpsMetadata(final double longitude, final double latitude) throws IOException {
        final TiffOutputSet set = new TiffOutputSet();
        set.getOrCreateRootDirectory();
        set.setGpsInDegrees(longitude, latitude);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new TiffImageWriterLossy().write(os, set);
        return (TiffImageMetadata) new TiffImageParser().getMetadata(ByteSource.array(os.toByteArray()), null);
    }

    @Test
    void testGpsInfo() throws IOException {
        final TiffImageMetadata.GpsInfo northEast = gpsMetadata(12.5, 41.75).getGpsInfo();
        assertEquals(41.75, northEast.getLatitudeAsDegreesNorth(), 1e-6);
        assertEquals(12.5, northEast.getLongitudeAsDegreesEast(), 1e-6);
        assertTrue(northEast.toString().startsWith("[GPS. Latitude: 41 degrees"));

        final TiffImageMetadata.GpsInfo southWest = gpsMetadata(-70.25, -33.5).getGpsInfo();
        assertEquals(-33.5, southWest.getLatitudeAsDegreesNorth(), 1e-6);
        assertEquals(-70.25, southWest.getLongitudeAsDegreesEast(), 1e-6);

        final TiffImageMetadata.GpsInfo invalid = new TiffImageMetadata.GpsInfo("X", "Y", HALF, HALF, HALF, HALF, HALF, HALF);
        assertThrows(ImagingException.class, invalid::getLatitudeAsDegreesNorth);
        assertThrows(ImagingException.class, invalid::getLongitudeAsDegreesEast);
    }
}
