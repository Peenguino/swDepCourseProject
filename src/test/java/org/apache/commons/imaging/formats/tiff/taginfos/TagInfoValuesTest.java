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
package org.apache.commons.imaging.formats.tiff.taginfos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.fieldtypes.AbstractFieldType;
import org.junit.jupiter.api.Test;

class TagInfoValuesTest {

    private static final TiffDirectoryType DIR = TiffDirectoryType.EXIF_DIRECTORY_GPS;

    @Test
    void testBaseTagInfo() throws Exception {
        final TagInfo tagInfo = new TagInfo("Test", 42, AbstractFieldType.LONG);
        assertEquals("Test", tagInfo.name);
        assertEquals(42, tagInfo.tag);
        assertEquals(TagInfo.LENGTH_UNKNOWN, tagInfo.length);
        assertEquals(TiffDirectoryType.EXIF_DIRECTORY_UNKNOWN, tagInfo.directoryType);
        assertFalse(tagInfo.isOffset());
        assertFalse(tagInfo.isText());
        assertTrue(tagInfo.getDescription().contains("Test"));
        assertTrue(tagInfo.toString().contains("42"));

        final TagInfo withLength = new TagInfo("Test", 42, AbstractFieldType.LONG, 2);
        assertEquals(2, withLength.length);
        final TagInfo withDirectory = new TagInfo("Test", 42, AbstractFieldType.LONG, 2, DIR);
        assertEquals(DIR, withDirectory.directoryType);
        final TagInfo offset = new TagInfo("Test", 42, AbstractFieldType.LONG, 2, DIR, true);
        assertTrue(offset.isOffset());
        final TagInfo list = new TagInfo("Test", 42, Arrays.asList(AbstractFieldType.LONG, AbstractFieldType.SHORT), 2, DIR);
        assertEquals(2, list.dataTypes.size());
        final TagInfo listOffset = new TagInfo("Test", 42, Arrays.asList(AbstractFieldType.LONG), 2, DIR, true);
        assertTrue(listOffset.isOffset());

        assertArrayEquals(new byte[] { 0, 0, 0, 7 }, tagInfo.encodeValue(AbstractFieldType.LONG, 7, ByteOrder.BIG_ENDIAN));
    }

    @Test
    void testLong() {
        final TagInfoLong tag = new TagInfoLong("Long", 1, DIR);
        assertArrayEquals(new byte[] { 0, 0, 0, 7 }, tag.encodeValue(ByteOrder.BIG_ENDIAN, 7));
        assertEquals(7, tag.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 0, 0, 0, 7 }));
        assertEquals(7, tag.getValue(ByteOrder.LITTLE_ENDIAN, new byte[] { 7, 0, 0, 0 }));
        assertTrue(new TagInfoLong("Long", 1, DIR, true).isOffset());
        assertEquals(1, new TagInfoLong("Long", 1, Arrays.asList(AbstractFieldType.LONG), 1, DIR, false).length);
    }

    @Test
    void testLongsAndSLongs() {
        final TagInfoLongs longs = new TagInfoLongs("Longs", 1, 2, DIR);
        assertArrayEquals(new byte[] { 0, 0, 0, 1, 0, 0, 0, 2 }, longs.encodeValue(ByteOrder.BIG_ENDIAN, 1, 2));
        assertArrayEquals(new int[] { 1, 2 }, longs.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 0, 0, 0, 1, 0, 0, 0, 2 }));
        assertTrue(new TagInfoLongs("Longs", 1, 2, DIR, true).isOffset());

        final TagInfoSLongs sLongs = new TagInfoSLongs("SLongs", 1, 2, DIR);
        assertArrayEquals(new int[] { -1, 2 }, sLongs.getValue(ByteOrder.BIG_ENDIAN, sLongs.encodeValue(ByteOrder.BIG_ENDIAN, -1, 2)));
        assertArrayEquals(new int[] { -1, 2 }, sLongs.getValue(ByteOrder.LITTLE_ENDIAN, sLongs.encodeValue(ByteOrder.LITTLE_ENDIAN, -1, 2)));
    }

    @Test
    void testShortAndShorts() {
        final TagInfoShort tag = new TagInfoShort("Short", 1, DIR);
        assertArrayEquals(new byte[] { 0, 7 }, tag.encodeValue(ByteOrder.BIG_ENDIAN, (short) 7));
        assertEquals((short) 7, tag.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 0, 7 }));

        final TagInfoShorts shorts = new TagInfoShorts("Shorts", 1, 2, DIR);
        assertArrayEquals(new short[] { 1, -2 }, shorts.getValue(ByteOrder.BIG_ENDIAN, shorts.encodeValue(ByteOrder.BIG_ENDIAN, (short) 1, (short) -2)));
        assertArrayEquals(new short[] { 1, -2 }, shorts.getValue(ByteOrder.LITTLE_ENDIAN, shorts.encodeValue(ByteOrder.LITTLE_ENDIAN, (short) 1, (short) -2)));

        final TagInfoSShorts sShorts = new TagInfoSShorts("SShorts", 1, 2, DIR);
        assertArrayEquals(new short[] { -3, 4 }, sShorts.getValue(ByteOrder.BIG_ENDIAN, sShorts.encodeValue(ByteOrder.BIG_ENDIAN, (short) -3, (short) 4)));
    }

    @Test
    void testByteVariants() {
        final TagInfoByte tag = new TagInfoByte("Byte", 1, DIR);
        assertArrayEquals(new byte[] { 7 }, tag.encodeValue(ByteOrder.BIG_ENDIAN, (byte) 7));
        assertEquals(1, new TagInfoByte("Byte", 1, AbstractFieldType.BYTE, DIR).length);
        assertEquals(1, new TagInfoByte("Byte", 1, Arrays.asList(AbstractFieldType.BYTE), DIR).dataTypes.size());

        final TagInfoBytes bytes = new TagInfoBytes("Bytes", 1, 2, DIR);
        assertArrayEquals(new byte[] { 1, 2 }, bytes.encodeValue(ByteOrder.BIG_ENDIAN, (byte) 1, (byte) 2));
        assertEquals(2, new TagInfoBytes("Bytes", 1, AbstractFieldType.BYTE, 2, DIR).length);
        assertEquals(1, new TagInfoBytes("Bytes", 1, Arrays.asList(AbstractFieldType.BYTE), 2, DIR).dataTypes.size());

        assertArrayEquals(new byte[] { -1 }, new TagInfoSByte("SByte", 1, DIR).encodeValue(ByteOrder.BIG_ENDIAN, (byte) -1));
        assertArrayEquals(new byte[] { -1, 2 }, new TagInfoSBytes("SBytes", 1, 2, DIR).encodeValue(ByteOrder.BIG_ENDIAN, (byte) -1, (byte) 2));
    }

    @Test
    void testFloatAndDouble() {
        final TagInfoFloat floatTag = new TagInfoFloat("Float", 1, DIR);
        assertEquals(1.5f, floatTag.getValue(ByteOrder.BIG_ENDIAN, floatTag.encodeValue(ByteOrder.BIG_ENDIAN, 1.5f)));
        final TagInfoFloats floats = new TagInfoFloats("Floats", 1, 2, DIR);
        assertArrayEquals(new float[] { 1.5f, -2.5f }, floats.getValue(ByteOrder.BIG_ENDIAN, floats.encodeValue(ByteOrder.BIG_ENDIAN, 1.5f, -2.5f)));

        final TagInfoDouble doubleTag = new TagInfoDouble("Double", 1, DIR);
        assertEquals(Math.PI, doubleTag.getValue(ByteOrder.BIG_ENDIAN, doubleTag.encodeValue(ByteOrder.BIG_ENDIAN, Math.PI)));
        final TagInfoDoubles doubles = new TagInfoDoubles("Doubles", 1, 2, DIR);
        assertArrayEquals(new double[] { Math.PI, -1.0 }, doubles.getValue(ByteOrder.BIG_ENDIAN, doubles.encodeValue(ByteOrder.BIG_ENDIAN, Math.PI, -1.0)));
    }

    @Test
    void testRationals() {
        // RationalNumber does not implement equals(): compare the exact textual rational form.
        final RationalNumber half = new RationalNumber(1, 2);
        final TagInfoRational rational = new TagInfoRational("Rational", 1, DIR);
        assertEquals(half.toString(), rational.getValue(ByteOrder.BIG_ENDIAN, rational.encodeValue(ByteOrder.BIG_ENDIAN, half)).toString());
        final TagInfoRationals rationals = new TagInfoRationals("Rationals", 1, 1, DIR);
        assertEquals(half.toString(), rationals.getValue(ByteOrder.BIG_ENDIAN, rationals.encodeValue(ByteOrder.BIG_ENDIAN, half))[0].toString());

        final RationalNumber negative = new RationalNumber(-1, 2);
        final TagInfoSRational sRational = new TagInfoSRational("SRational", 1, DIR);
        assertEquals(negative.toString(), sRational.getValue(ByteOrder.BIG_ENDIAN, sRational.encodeValue(ByteOrder.BIG_ENDIAN, negative)).toString());
        final TagInfoSRationals sRationals = new TagInfoSRationals("SRationals", 1, 1, DIR);
        assertEquals(negative.toString(), sRationals.getValue(ByteOrder.BIG_ENDIAN, sRationals.encodeValue(ByteOrder.BIG_ENDIAN, negative))[0].toString());
    }

    @Test
    void testCompositeTags() {
        final TagInfoShortOrLong shortOrLong = new TagInfoShortOrLong("SoL", 1, 1, DIR);
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, shortOrLong.encodeValue(ByteOrder.BIG_ENDIAN, 1));
        assertArrayEquals(new byte[] { 0, 1 }, shortOrLong.encodeValue(ByteOrder.BIG_ENDIAN, (short) 1));
        assertTrue(new TagInfoShortOrLong("SoL", 1, 1, DIR, true).isOffset());

        final TagInfoShortOrRational shortOrRational = new TagInfoShortOrRational("SoR", 1, 1, DIR);
        assertArrayEquals(new byte[] { 0, 1 }, shortOrRational.encodeValue(ByteOrder.BIG_ENDIAN, (short) 1));
        assertEquals(8, shortOrRational.encodeValue(ByteOrder.BIG_ENDIAN, new RationalNumber(1, 2)).length);

        final TagInfoShortOrLongOrRational all = new TagInfoShortOrLongOrRational("All", 1, 1, DIR);
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, all.encodeValue(ByteOrder.BIG_ENDIAN, 1));
        assertArrayEquals(new byte[] { 0, 1 }, all.encodeValue(ByteOrder.BIG_ENDIAN, (short) 1));
        assertEquals(8, all.encodeValue(ByteOrder.BIG_ENDIAN, new RationalNumber(1, 2)).length);

        final TagInfoLongOrIfd longOrIfd = new TagInfoLongOrIfd("Ifd", 1, 1, DIR);
        assertArrayEquals(new int[] { 3 }, longOrIfd.getValue(ByteOrder.BIG_ENDIAN, longOrIfd.encodeValue(ByteOrder.BIG_ENDIAN, 3)));
        assertTrue(new TagInfoLongOrIfd("Ifd", 1, 1, DIR, true).isOffset());

        final TagInfoByteOrShort byteOrShort = new TagInfoByteOrShort("BoS", 1, 1, DIR);
        assertArrayEquals(new byte[] { 1 }, byteOrShort.encodeValue(ByteOrder.BIG_ENDIAN, (byte) 1));
        assertArrayEquals(new byte[] { 0, 1 }, byteOrShort.encodeValue(ByteOrder.BIG_ENDIAN, (short) 1));
    }

    @Test
    void testMarkerTags() {
        assertEquals(1, new TagInfoAsciiOrRational("AoR", 1, 1, DIR).length);
        assertEquals(1, new TagInfoAsciiOrByte("AoB", 1, 1, DIR).length);
        assertEquals(1, new TagInfoAny("Any", 1, 1, DIR).length);
        assertEquals(DIR, new TagInfoUnknown("Unknown", 1, DIR).directoryType);
        assertEquals(1, new TagInfoUnknowns("Unknowns", 1, 1, DIR).length);
        assertEquals(DIR, new TagInfoUndefined("Undefined", 1, DIR).directoryType);
        assertEquals(1, new TagInfoUndefineds("Undefineds", 1, 1, DIR).length);
        assertEquals(DIR, new TagInfoDirectory("Directory", 1, DIR).directoryType);
    }
}
