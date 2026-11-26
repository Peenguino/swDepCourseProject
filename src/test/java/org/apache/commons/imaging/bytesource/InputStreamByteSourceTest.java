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
package org.apache.commons.imaging.bytesource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.commons.imaging.ImagingException;
import org.junit.jupiter.api.Test;

class InputStreamByteSourceTest {

    /** Larger than two 8192-byte blocks, to exercise block chaining. */
    private static final int DATA_LENGTH = 20000;

    private static byte[] testData() {
        final byte[] data = new byte[DATA_LENGTH];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    private static ByteSource byteSource(final byte[] data) throws IOException {
        return ByteSource.inputStream(new ByteArrayInputStream(data), "test");
    }

    @Test
    void testSingleByteReadsAcrossBlocks() throws IOException {
        final byte[] data = testData();
        try (InputStream is = byteSource(data).getInputStream()) {
            for (final byte expected : data) {
                assertEquals(0xff & expected, is.read());
            }
            assertEquals(-1, is.read());
            assertEquals(-1, is.read(), "reads past EOF must keep returning -1");
        }
    }

    @Test
    void testBulkReadsAcrossBlocks() throws IOException {
        final byte[] data = testData();
        final byte[] readBack = new byte[data.length];
        try (InputStream is = byteSource(data).getInputStream()) {
            int total = 0;
            while (total < data.length) {
                final int chunk = Math.min(1000, data.length - total);
                final int read = is.read(readBack, total, chunk);
                // A bulk read may return fewer bytes than requested (block-local reads).
                assertTrue(read > 0 && read <= chunk, "unexpected read size: " + read);
                total += read;
            }
            assertEquals(-1, is.read(readBack, 0, 10));
        }
        assertArrayEquals(data, readBack);
    }

    @Test
    void testBulkReadWithNonZeroOffset() throws IOException {
        final byte[] data = testData();
        final byte[] buffer = new byte[64];
        Arrays.fill(buffer, (byte) 0x55);
        try (InputStream is = byteSource(data).getInputStream()) {
            assertEquals(10, is.read(buffer, 5, 10));
        }
        assertEquals(0x55, buffer[4] & 0xff, "bytes before the offset must be untouched");
        for (int i = 0; i < 10; i++) {
            assertEquals(data[i], buffer[5 + i]);
        }
        assertEquals(0x55, buffer[15] & 0xff, "bytes after the read region must be untouched");
    }

    @Test
    void testBulkReadArgumentValidation() throws IOException {
        final byte[] data = testData();
        try (InputStream is = byteSource(data).getInputStream()) {
            final byte[] buffer = new byte[16];
            assertThrows(NullPointerException.class, () -> is.read(null, 0, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> is.read(buffer, -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> is.read(buffer, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> is.read(buffer, 17, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> is.read(buffer, 15, 2));
            assertEquals(0, is.read(buffer, 0, 0), "a zero-length read must return 0 even at EOF");
        }
    }

    @Test
    void testZeroLengthReadAtEof() throws IOException {
        final byte[] data = { 1 };
        try (InputStream is = byteSource(data).getInputStream()) {
            assertEquals(1, is.read());
            assertEquals(-1, is.read());
            assertEquals(0, is.read(new byte[4], 0, 0));
        }
    }

    @Test
    void testSkip() throws IOException {
        final byte[] data = testData();
        try (InputStream is = byteSource(data).getInputStream()) {
            assertEquals(0, is.skip(0));
            assertEquals(0, is.skip(-10));
            assertEquals(100, is.skip(100));
            assertEquals(0xff & data[100], is.read());
            // Skip across a block boundary.
            assertEquals(9000, is.skip(9000));
            assertEquals(0xff & data[9101], is.read());
            // Skipping more than the remaining bytes returns the actually skipped amount.
            final long remaining = data.length - 9102;
            assertEquals(remaining, is.skip(remaining + 500));
        }
    }

    @Test
    void testIndependentInputStreams() throws IOException {
        final byte[] data = testData();
        final ByteSource byteSource = byteSource(data);
        try (InputStream first = byteSource.getInputStream(); InputStream second = byteSource.getInputStream()) {
            assertEquals(0xff & data[0], first.read());
            assertEquals(0xff & data[1], first.read());
            assertEquals(0xff & data[0], second.read(), "each stream must start from the beginning");
        }
    }

    @Test
    void testSizeIsCachedAndCorrect() throws IOException {
        final byte[] data = testData();
        final ByteSource byteSource = byteSource(data);
        assertEquals(data.length, byteSource.size());
        assertEquals(data.length, byteSource.size());
    }

    @Test
    void testEmptyStream() throws IOException {
        final ByteSource byteSource = byteSource(new byte[0]);
        assertEquals(0, byteSource.size());
        try (InputStream is = byteSource.getInputStream()) {
            assertEquals(-1, is.read());
            assertEquals(-1, is.read(new byte[8], 0, 8));
        }
    }

    @Test
    void testGetByteArray() throws IOException {
        final byte[] data = testData();
        final ByteSource byteSource = byteSource(data);
        assertArrayEquals(Arrays.copyOfRange(data, 0, 10), byteSource.getByteArray(0, 10));
        // Slice crossing a block boundary.
        assertArrayEquals(Arrays.copyOfRange(data, 8000, 9000), byteSource.getByteArray(8000, 1000));
        // NOTE: a zero-length request currently fails with "Could not read block." because the
        // read loop treats the 0 returned by a zero-length read as an error. Documented here as-is.
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(5, 0));
    }

    @Test
    void testGetByteArrayInvalidRanges() throws IOException {
        final ByteSource byteSource = byteSource(testData());
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(-1, 10));
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(0, -1));
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(0, DATA_LENGTH + 1));
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(DATA_LENGTH - 5, 10));
        assertThrows(ImagingException.class, () -> byteSource.getByteArray(Long.MAX_VALUE - 1, 10));
    }
}
