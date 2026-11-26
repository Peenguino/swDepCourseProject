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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.fieldtypes.AbstractFieldType;
import org.junit.jupiter.api.Test;

class TagInfoGpsTextTest {

    private static final TagInfoGpsText TAG = new TagInfoGpsText("GPS", 1, TiffDirectoryType.EXIF_DIRECTORY_GPS);

    private static TiffField field(final AbstractFieldType type, final byte[] bytes) {
        return new TiffField(1, 0, type, bytes.length, 0, bytes, ByteOrder.BIG_ENDIAN, 1);
    }

    private static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Test
    void testEncodeAscii() throws Exception {
        final byte[] encoded = TAG.encodeValue(AbstractFieldType.UNDEFINED, "hello", ByteOrder.BIG_ENDIAN);
        assertEquals(8 + 5, encoded.length);
        assertEquals("ASCII", new String(encoded, 0, 5, StandardCharsets.US_ASCII));
        assertEquals("hello", TAG.getValue(field(AbstractFieldType.UNDEFINED, encoded)));
    }

    @Test
    void testEncodeNonAsciiBigEndian() throws Exception {
        final byte[] encoded = TAG.encodeValue(AbstractFieldType.UNDEFINED, "€", ByteOrder.BIG_ENDIAN);
        assertEquals("UNICODE", new String(encoded, 0, 7, StandardCharsets.US_ASCII));
        assertEquals("€", new String(encoded, 8, encoded.length - 8, StandardCharsets.UTF_16BE));
    }

    @Test
    void testGetValueBigEndianIsMisdecodedAsLittleEndian() throws Exception {
        // NOTE: getValue tries UTF-16LE before UTF-16BE for the same "UNICODE" prefix, and the
        // LE reinterpretation always round-trips, so BE payloads are currently misdecoded.
        // This test documents the existing (buggy) behavior.
        final byte[] encoded = TAG.encodeValue(AbstractFieldType.UNDEFINED, "€", ByteOrder.BIG_ENDIAN);
        assertEquals("갠", TAG.getValue(field(AbstractFieldType.UNDEFINED, encoded)));
    }

    @Test
    void testEncodeNonAsciiLittleEndian() throws Exception {
        final byte[] encoded = TAG.encodeValue(AbstractFieldType.UNDEFINED, "€", ByteOrder.LITTLE_ENDIAN);
        assertEquals("€", TAG.getValue(field(AbstractFieldType.UNDEFINED, encoded)));
        assertEquals("€", new String(encoded, 8, encoded.length - 8, StandardCharsets.UTF_16LE));
    }

    @Test
    void testEncodeRejectsNonString() {
        final ImagingException e = assertThrows(ImagingException.class, () -> TAG.encodeValue(AbstractFieldType.UNDEFINED, 42, ByteOrder.BIG_ENDIAN));
        assertTrue(e.getMessage().contains("not String"), e.getMessage());
    }

    @Test
    void testGetValueFromAsciiField() throws Exception {
        final byte[] bytes = "hello".getBytes(StandardCharsets.US_ASCII);
        assertEquals("hello", TAG.getValue(field(AbstractFieldType.ASCII, bytes)));
    }

    @Test
    void testGetValueFromAsciiFieldWithMultipleStrings() throws Exception {
        // Two NUL-separated strings: the first one is returned.
        final byte[] bytes = "first".getBytes(StandardCharsets.US_ASCII);
        assertEquals("first", TAG.getValue(field(AbstractFieldType.ASCII, concat(bytes, new byte[] { 0, 'x', 0 }))));
    }

    @Test
    void testGetValueFromShortByteArray() throws Exception {
        assertEquals("abc", TAG.getValue(field(AbstractFieldType.BYTE, new byte[] { 'a', 'b', 'c' })));
    }

    @Test
    void testGetValueWithUndefinedEncodingPrefix() throws Exception {
        final byte[] bytes = concat(new byte[8], "café".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("café", TAG.getValue(field(AbstractFieldType.UNDEFINED, bytes)));
    }

    @Test
    void testGetValueWithJisPrefix() throws Exception {
        final byte[] prefix = { 'J', 'I', 'S', 0, 0, 0, 0, 0 };
        final byte[] bytes = concat(prefix, "abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals("abc", TAG.getValue(field(AbstractFieldType.UNDEFINED, bytes)));
    }

    @Test
    void testGetValueWithUnknownPrefixFallsBackToAscii() throws Exception {
        final byte[] bytes = concat("XXXXXXXX".getBytes(StandardCharsets.US_ASCII), "abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals("XXXXXXXXabc", TAG.getValue(field(AbstractFieldType.UNDEFINED, bytes)));
    }

    @Test
    void testGetValueRejectsNonByteField() {
        final byte[] bytes = { 0, 0, 0, 7 };
        final ImagingException e = assertThrows(ImagingException.class, () -> TAG.getValue(field(AbstractFieldType.LONG, bytes)));
        assertTrue(e.getMessage().contains("not encoded as bytes"), e.getMessage());
    }

    @Test
    void testIsText() {
        assertTrue(TAG.isText());
    }
}
