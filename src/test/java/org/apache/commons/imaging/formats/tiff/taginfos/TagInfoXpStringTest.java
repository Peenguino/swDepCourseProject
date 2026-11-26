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

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.fieldtypes.AbstractFieldType;
import org.junit.jupiter.api.Test;

class TagInfoXpStringTest {

    private static final TagInfoXpString TAG = new TagInfoXpString("XP", 1, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD);

    private static TiffField field(final AbstractFieldType type, final byte[] bytes) {
        return new TiffField(1, 0, type, bytes.length, 0, bytes, ByteOrder.LITTLE_ENDIAN, 1);
    }

    @Test
    void testEncodeAddsNullTerminatorAndRoundTrips() throws Exception {
        final byte[] encoded = TAG.encodeValue(AbstractFieldType.BYTE, "hi", ByteOrder.LITTLE_ENDIAN);
        assertEquals(6, encoded.length, "two UTF-16LE characters plus a two-byte terminator");
        assertEquals(0, encoded[encoded.length - 1]);
        assertEquals(0, encoded[encoded.length - 2]);
        assertEquals("hi", TAG.getValue(field(AbstractFieldType.BYTE, encoded)));
    }

    @Test
    void testEncodeRejectsNonString() {
        assertThrows(ImagingException.class, () -> TAG.encodeValue(AbstractFieldType.BYTE, 42, ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void testGetValueWithoutTerminator() throws Exception {
        final byte[] bytes = "hi".getBytes(StandardCharsets.UTF_16LE);
        assertEquals("hi", TAG.getValue(field(AbstractFieldType.BYTE, bytes)));
    }

    @Test
    void testGetValueNonAsciiWithoutTerminator() throws Exception {
        // Last byte non-zero: no terminator is stripped.
        final byte[] bytes = "€".getBytes(StandardCharsets.UTF_16LE);
        assertEquals("€", TAG.getValue(field(AbstractFieldType.BYTE, bytes)));
    }

    @Test
    void testGetValueRejectsNonByteField() {
        assertThrows(ImagingException.class, () -> TAG.getValue(field(AbstractFieldType.LONG, new byte[] { 0, 0, 0, 0 })));
    }
}
