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

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.junit.jupiter.api.Test;

class TagInfoAsciiTest {

    private static final TagInfoAscii TAG = new TagInfoAscii("Ascii", 1, 10, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD);

    @Test
    void testEncodeValue() throws Exception {
        final byte[] encoded = TAG.encodeValue(ByteOrder.BIG_ENDIAN, "a", "b");
        assertArrayEquals(new byte[] { 'a', 0, 'b', 0 }, encoded);
    }

    @Test
    void testGetValueTwoNullTerminatedStrings() {
        assertArrayEquals(new String[] { "a", "b" }, TAG.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 'a', 0, 'b', 0 }));
    }

    @Test
    void testGetValueUnterminatedString() {
        // Buggy input without a NUL terminator must still be decoded.
        assertArrayEquals(new String[] { "abc" }, TAG.getValue(ByteOrder.BIG_ENDIAN, "abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testGetValueUnterminatedSecondString() {
        assertArrayEquals(new String[] { "a", "b" }, TAG.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 'a', 0, 'b' }));
    }

    @Test
    void testGetValueEmptyString() {
        assertArrayEquals(new String[] { "" }, TAG.getValue(ByteOrder.BIG_ENDIAN, new byte[] { 0 }));
    }

    @Test
    void testGetValueUtf8() {
        final byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
        final byte[] terminated = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, terminated, 0, bytes.length);
        assertArrayEquals(new String[] { "café" }, TAG.getValue(ByteOrder.BIG_ENDIAN, terminated));
    }
}
