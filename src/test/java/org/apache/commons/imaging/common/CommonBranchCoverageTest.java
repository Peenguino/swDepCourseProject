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
package org.apache.commons.imaging.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.imaging.ImagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers error and edge branches of the small helpers in the {@code common} package.
 */
class CommonBranchCoverageTest {

    @TempDir
    Path tempDir;

    @Test
    void testBinaryConstantEqualsAndHashCode() {
        final BinaryConstant a = new BinaryConstant(new byte[] { 1, 2, 3 });
        final BinaryConstant same = new BinaryConstant(new byte[] { 1, 2, 3 });
        final BinaryConstant other = new BinaryConstant(new byte[] { 1, 2, 4 });

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(null, a);
        assertFalse(a.equals((Object) null));
        assertFalse(a.equals("not a constant"));
        assertTrue(a.equals(new byte[] { 1, 2, 3 }));
    }

    @Test
    void testRationalFactoryMethodReducesLargeValues() {
        final RationalNumber reduced = RationalNumber.factoryMethod(1L << 40, 3L << 40);
        assertEquals(1.0 / 3.0, reduced.doubleValue(), 1e-9);

        final RationalNumber negative = RationalNumber.factoryMethod(-(1L << 40), 1L << 39);
        assertEquals(-2.0, negative.doubleValue(), 1e-9);

        // out-of-range numerator with a zero divisor cannot be reduced
        assertThrows(NumberFormatException.class, () -> RationalNumber.factoryMethod(Long.MAX_VALUE, 0));
    }

    @Test
    void testRationalValueOfSmallFractions() {
        assertEquals(0.3, RationalNumber.valueOf(0.3).doubleValue(), 1e-8);
        assertEquals(0.6, RationalNumber.valueOf(0.6).doubleValue(), 1e-8);
        assertEquals(0.0123, RationalNumber.valueOf(0.0123).doubleValue(), 1e-8);
        assertEquals(-2.5, RationalNumber.valueOf(-2.5).doubleValue(), 1e-8);
    }

    @Test
    void testRationalNegateAndStrings() {
        final RationalNumber big = new RationalNumber(0x80000000, 2, true);
        final RationalNumber negated = big.negate();
        assertEquals(-big.doubleValue(), negated.doubleValue(), 1e-6);

        final RationalNumber notReducible = new RationalNumber(0x80000001, 2, true);
        assertThrows(NumberFormatException.class, notReducible::negate);

        assertEquals("3", new RationalNumber(6, 2).toDisplayString());
        assertTrue(new RationalNumber(1, 3).toDisplayString().contains("333"));
        assertEquals("Invalid rational (1/0)", new RationalNumber(1, 0).toString());
    }

    @Test
    void testCompareBytesOutOfRange() {
        final byte[] a = { 1, 2, 3 };
        final byte[] b = { 1, 2, 3, 4 };
        assertFalse(BinaryFunctions.compareBytes(a, 2, b, 0, 3));
        assertFalse(BinaryFunctions.compareBytes(b, 0, a, 2, 3));
        assertTrue(BinaryFunctions.compareBytes(a, 0, b, 0, 3));
    }

    @Test
    void testGetBytesFromRandomAccessFile() throws IOException {
        final File file = tempDir.resolve("data.bin").toFile();
        Files.write(file.toPath(), new byte[] { 10, 20, 30, 40 });
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertArrayEquals(new byte[] { 20, 30 }, BinaryFunctions.getBytes(raf, 1, 2, "msg"));
            final IOException e = assertThrows(IOException.class, () -> BinaryFunctions.getBytes(raf, 0, -1, "bad length"));
            assertTrue(e.getMessage().contains("bad length"));
        }
    }

    @Test
    void testLoggingAndPrintingHelpers() {
        BinaryFunctions.logByteBits("bits", (byte) 5);
        final StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            BinaryFunctions.printCharQuad(pw, "quad", BinaryFunctions.charsToQuad('A', 'B', 'C', 'D'));
        }
        assertTrue(sw.toString().contains("quad: 'ABCD"));
    }

    @Test
    void testReadMultiByteValuesAndEof() throws IOException {
        final byte[] data = { 1, 2, 3 };
        assertEquals(0x010203, BinaryFunctions.read3Bytes("n", new ByteArrayInputStream(data), "eof", ByteOrder.BIG_ENDIAN));
        assertEquals(0x030201, BinaryFunctions.read3Bytes("n", new ByteArrayInputStream(data), "eof", ByteOrder.LITTLE_ENDIAN));
        assertThrows(IOException.class, () -> BinaryFunctions.read3Bytes("n", new ByteArrayInputStream(new byte[2]), "eof", ByteOrder.BIG_ENDIAN));
        assertThrows(IOException.class, () -> BinaryFunctions.read8Bytes("n", new ByteArrayInputStream(new byte[7]), "eof", ByteOrder.BIG_ENDIAN));
        assertThrows(IOException.class, () -> BinaryFunctions.readByte("n", new ByteArrayInputStream(new byte[0]), "eof"));
    }

    @Test
    void testReadAndVerifyBytes() throws IOException {
        BinaryFunctions.readAndVerifyBytes(new ByteArrayInputStream(new byte[] { 7, 8 }), new byte[] { 7, 8 }, "mismatch");
        final ImagingException mismatch = assertThrows(ImagingException.class,
                () -> BinaryFunctions.readAndVerifyBytes(new ByteArrayInputStream(new byte[] { 7, 9 }), new byte[] { 7, 8 }, "mismatch"));
        assertEquals("mismatch", mismatch.getMessage());
        final ImagingException eof = assertThrows(ImagingException.class,
                () -> BinaryFunctions.readAndVerifyBytes(new ByteArrayInputStream(new byte[] { 7 }), new byte[] { 7, 8 }, "mismatch"));
        assertEquals("Unexpected EOF.", eof.getMessage());
    }

    @Test
    void testSkipBytesWrapsIOException() {
        final InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        final IOException e = assertThrows(IOException.class, () -> BinaryFunctions.skipBytes(failing, 10, "cannot skip"));
        assertEquals("cannot skip", e.getMessage());
    }

    @Test
    void testStartsWithNullArguments() {
        assertFalse(BinaryFunctions.startsWith(new byte[] { 1 }, null));
        assertFalse(BinaryFunctions.startsWith(null, new byte[] { 1 }));
        assertTrue(BinaryFunctions.startsWith(new byte[] { 1, 2 }, new byte[] { 1 }));
    }

    @Test
    void testBigEndianStreamAndZlibDeflate() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (BigEndianBinaryOutputStream out = new BigEndianBinaryOutputStream(bos)) {
            out.write2Bytes(0x0102);
            out.write3Bytes(0x030405);
        }
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, bos.toByteArray());

        final byte[] payload = "hello hello hello hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        final byte[] compressed = ZlibDeflate.compress(payload);
        assertArrayEquals(payload, ZlibDeflate.decompress(compressed, payload.length));
        final ImagingException e = assertThrows(ImagingException.class, () -> ZlibDeflate.decompress(new byte[] { 1, 2, 3, 4 }, 10));
        assertEquals("Unable to decompress image", e.getMessage());
    }
}
