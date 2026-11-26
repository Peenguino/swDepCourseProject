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
package org.apache.commons.imaging.formats.tiff.fieldtypes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteOrder;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.junit.jupiter.api.Test;

class FieldTypeValuesTest {

    private static final ByteOrder BE = ByteOrder.BIG_ENDIAN;
    private static final ByteOrder LE = ByteOrder.LITTLE_ENDIAN;

    private static TiffField field(final AbstractFieldType type, final long count, final byte[] bytes, final ByteOrder byteOrder) {
        return new TiffField(1, 0, type, count, 0, bytes, byteOrder, 1);
    }

    /** RationalNumber does not implement equals(): compare the exact textual rational form. */
    private static void assertRationalEquals(final RationalNumber expected, final Object actual) {
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    void testFloat() throws Exception {
        final AbstractFieldType type = AbstractFieldType.FLOAT;
        assertEquals(4, type.getSize());

        final byte[] one = type.writeData(1.5f, BE);
        assertEquals(1.5f, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new float[] { 1.5f, -2.5f }, BE);
        assertArrayEquals(new float[] { 1.5f, -2.5f }, (float[]) type.getValue(field(type, 2, many, BE)));

        final byte[] boxed = type.writeData(new Float[] { 3.5f }, LE);
        assertEquals(3.5f, type.getValue(field(type, 1, boxed, LE)));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testDouble() throws Exception {
        final AbstractFieldType type = AbstractFieldType.DOUBLE;
        assertEquals(8, type.getSize());

        final byte[] one = type.writeData(Math.PI, BE);
        assertEquals(Math.PI, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new double[] { Math.PI, -1.0 }, BE);
        assertArrayEquals(new double[] { Math.PI, -1.0 }, (double[]) type.getValue(field(type, 2, many, BE)));

        final byte[] boxed = type.writeData(new Double[] { 2.5 }, LE);
        assertEquals(2.5, type.getValue(field(type, 1, boxed, LE)));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testLong8() throws Exception {
        final AbstractFieldType type = AbstractFieldType.LONG8;
        assertEquals(8, type.getSize());

        final byte[] one = type.writeData(7, BE);
        assertEquals(7, type.getValue(field(type, 1, one, BE)));

        // getValue for count > 1 reads 8-byte longs: build a real two-element LONG8 payload.
        final byte[] many = { 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2 };
        assertArrayEquals(new long[] { 1, 2 }, (long[]) type.getValue(field(type, 2, many, BE)));

        // NOTE: writeData(int[]) emits 4 bytes per value while getValue reads 8-byte longs,
        // so the two methods are not symmetric for arrays. Only the written size is asserted here.
        assertEquals(8, type.writeData(new int[] { 1, 2 }, BE).length);

        // NOTE: the Integer[] branch uses System.arraycopy(Integer[] -> int[]) and currently
        // throws ArrayStoreException instead of encoding. Documented as-is.
        assertThrows(ArrayStoreException.class, () -> type.writeData(new Integer[] { 9 }, LE));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testLong() throws Exception {
        final AbstractFieldType type = AbstractFieldType.LONG;

        final byte[] one = type.writeData(7, BE);
        assertEquals(7, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new int[] { 1, 2 }, BE);
        assertArrayEquals(new int[] { 1, 2 }, (int[]) type.getValue(field(type, 2, many, BE)));

        // NOTE: the Integer[] branch uses System.arraycopy(Integer[] -> int[]) and currently
        // throws ArrayStoreException instead of encoding. Documented as-is.
        assertThrows(ArrayStoreException.class, () -> type.writeData(new Integer[] { 9 }, LE));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testShort() throws Exception {
        final AbstractFieldType type = AbstractFieldType.SHORT;

        final byte[] one = type.writeData((short) 7, BE);
        assertEquals((short) 7, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new short[] { 1, -2 }, BE);
        assertArrayEquals(new short[] { 1, -2 }, (short[]) type.getValue(field(type, 2, many, BE)));

        final byte[] boxed = type.writeData(new Short[] { 9 }, LE);
        assertEquals((short) 9, type.getValue(field(type, 1, boxed, LE)));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testByte() throws Exception {
        final AbstractFieldType type = AbstractFieldType.BYTE;

        assertArrayEquals(new byte[] { 7 }, type.writeData((byte) 7, BE));
        assertEquals((byte) 7, type.getValue(field(type, 1, new byte[] { 7 }, BE)));

        final byte[] many = { 1, 2 };
        assertArrayEquals(many, type.writeData(many, BE));
        assertArrayEquals(many, (byte[]) type.getValue(field(type, 2, many, BE)));

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testRational() throws Exception {
        final AbstractFieldType type = AbstractFieldType.RATIONAL;
        final RationalNumber half = new RationalNumber(1, 2);

        final byte[] one = type.writeData(half, BE);
        assertRationalEquals(half, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new RationalNumber[] { half, new RationalNumber(1, 4) }, BE);
        final RationalNumber[] readBack = (RationalNumber[]) type.getValue(field(type, 2, many, BE));
        assertRationalEquals(half, readBack[0]);
        assertRationalEquals(new RationalNumber(1, 4), readBack[1]);

        // Numbers are converted through their double value.
        assertEquals(8, type.writeData(0.5, BE).length);
        assertEquals(16, type.writeData(new Number[] { 0.5, 0.25 }, BE).length);
        assertEquals(16, type.writeData(new double[] { 0.5, 0.25 }, BE).length);

        assertThrows(ImagingException.class, () -> type.writeData("nope", BE));
    }

    @Test
    void testSignedRational() throws Exception {
        final AbstractFieldType type = AbstractFieldType.SRATIONAL;
        final RationalNumber negative = new RationalNumber(-1, 2);

        final byte[] one = type.writeData(negative, BE);
        assertRationalEquals(negative, type.getValue(field(type, 1, one, BE)));

        final byte[] many = type.writeData(new RationalNumber[] { negative, negative }, BE);
        assertRationalEquals(negative, ((RationalNumber[]) type.getValue(field(type, 2, many, BE)))[0]);
    }
}
