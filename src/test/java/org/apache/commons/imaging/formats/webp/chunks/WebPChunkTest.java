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
package org.apache.commons.imaging.formats.webp.chunks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.imaging.ImagingException;
import org.junit.jupiter.api.Test;

class WebPChunkTest {

    /** Builds a chunk type code from its FourCC, in little-endian order. */
    private static int fourCc(final String fourCc) {
        return fourCc.charAt(0) | fourCc.charAt(1) << 8 | fourCc.charAt(2) << 16 | fourCc.charAt(3) << 24;
    }

    private static byte[] validVp8Payload() {
        final byte[] bytes = new byte[10];
        bytes[0] = 0b0001_0000; // key frame, version 0, show_frame = 1
        bytes[3] = (byte) 0x9D;
        bytes[4] = 0x01;
        bytes[5] = 0x2A;
        bytes[6] = 40; // width = 40
        bytes[8] = 30; // height = 30
        return bytes;
    }

    @Test
    void testIccpChunk() throws ImagingException {
        final byte[] payload = { 1, 2, 3 };
        final WebPChunkIccp chunk = new WebPChunkIccp(fourCc("ICCP"), payload.length, payload);
        assertEquals("ICCP", chunk.getTypeDescription());
        assertEquals(3, chunk.getPayloadSize());
        assertArrayEquals(payload, chunk.getBytes());
        assertThrows(ImagingException.class, () -> new WebPChunkIccp(fourCc("ICCP"), 4, payload));
    }

    @Test
    void testAlphChunk() throws ImagingException {
        final byte[] payload = { 0, 9 };
        final WebPChunkAlph chunk = new WebPChunkAlph(fourCc("ALPH"), payload.length, payload);
        assertEquals("ALPH", chunk.getTypeDescription());
        assertEquals(2, chunk.getPayloadSize());
        assertArrayEquals(payload, chunk.getBytes());
        assertThrows(ImagingException.class, () -> new WebPChunkAlph(fourCc("ALPH"), 3, payload));
    }

    @Test
    void testVp8ChunkParsesDimensions() throws ImagingException {
        final byte[] payload = validVp8Payload();
        final WebPChunkVp8 chunk = new WebPChunkVp8(fourCc("VP8 "), payload.length, payload);
        assertEquals(0, chunk.getVersionNumber());
        assertEquals(40, chunk.getWidth());
        assertEquals(30, chunk.getHeight());
        assertEquals(0, chunk.getHorizontalScale());
        assertEquals(0, chunk.getVerticalScale());
    }

    @Test
    void testVp8ChunkTooShort() {
        final byte[] payload = new byte[9];
        assertThrows(ImagingException.class, () -> new WebPChunkVp8(fourCc("VP8 "), payload.length, payload));
    }

    @Test
    void testVp8ChunkRejectsInterFrame() {
        final byte[] payload = validVp8Payload();
        payload[0] |= 0b1; // frame type = interframe
        final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8(fourCc("VP8 "), payload.length, payload));
        assertTrue(e.getMessage().contains("key frame"), e.getMessage());
    }

    @Test
    void testVp8ChunkRejectsHiddenFrame() {
        final byte[] payload = validVp8Payload();
        payload[0] = 0; // show_frame = 0
        final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8(fourCc("VP8 "), payload.length, payload));
        assertTrue(e.getMessage().contains("display"), e.getMessage());
    }

    @Test
    void testVp8ChunkRejectsBadSignature() {
        // Each of the three signature bytes, corrupted one at a time.
        for (int i = 3; i <= 5; i++) {
            final byte[] payload = validVp8Payload();
            payload[i] = 0;
            final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8(fourCc("VP8 "), payload.length, payload));
            assertTrue(e.getMessage().contains("signature"), e.getMessage());
        }
    }

    @Test
    void testVp8lChunkParsesDimensions() throws ImagingException {
        final byte[] payload = { 0x2f, 39, 0, 0, 0 }; // width = 39 + 1, height = 0 + 1
        final WebPChunkVp8l chunk = new WebPChunkVp8l(fourCc("VP8L"), payload.length, payload);
        assertEquals(40, chunk.getImageWidth());
        assertEquals(1, chunk.getImageHeight());
        assertFalse(chunk.hasAlpha());
        assertEquals(0, chunk.getVersionNumber());
    }

    @Test
    void testVp8lChunkRejectsBadSignatureAndShortSize() {
        final byte[] badSignature = { 0, 0, 0, 0, 0 };
        assertThrows(ImagingException.class, () -> new WebPChunkVp8l(fourCc("VP8L"), badSignature.length, badSignature));

        final byte[] tooShort = { 0x2f, 0, 0, 0 };
        assertThrows(ImagingException.class, () -> new WebPChunkVp8l(fourCc("VP8L"), tooShort.length, tooShort));
    }

    @Test
    void testVp8lChunkRejectsNonZeroVersion() {
        final byte[] payload = { 0x2f, 0, 0, 0, (byte) 0b0010_0000 }; // version = 1
        final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8l(fourCc("VP8L"), payload.length, payload));
        assertTrue(e.getMessage().contains("version"), e.getMessage());
    }

    @Test
    void testVp8xChunkParsesFlagsAndCanvas() throws ImagingException {
        final byte[] payload = new byte[10];
        payload[0] = 0b0011_1110; // ICC, alpha, EXIF, XMP, animation
        payload[4] = 39; // canvas width = 39 + 1
        payload[7] = 29; // canvas height = 29 + 1
        final WebPChunkVp8x chunk = new WebPChunkVp8x(fourCc("VP8X"), payload.length, payload);
        assertTrue(chunk.hasIcc());
        assertTrue(chunk.hasAlpha());
        assertTrue(chunk.hasExif());
        assertTrue(chunk.hasXmp());
        assertTrue(chunk.hasAnimation());
        assertEquals(40, chunk.getCanvasWidth());
        assertEquals(30, chunk.getCanvasHeight());
    }

    @Test
    void testVp8xChunkRejectsWrongSize() {
        final byte[] payload = new byte[9];
        final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8x(fourCc("VP8X"), payload.length, payload));
        assertTrue(e.getMessage().contains("must be 10"), e.getMessage());
    }

    @Test
    void testVp8xChunkRejectsOverflowingCanvas() {
        final byte[] payload = new byte[10];
        // width = 2^20, height = 2^11: their product overflows int and becomes negative.
        payload[4] = (byte) 0xFF;
        payload[5] = (byte) 0xFF;
        payload[6] = 0x0F;
        payload[7] = (byte) 0xFF;
        payload[8] = 0x07;
        final ImagingException e = assertThrows(ImagingException.class, () -> new WebPChunkVp8x(fourCc("VP8X"), payload.length, payload));
        assertTrue(e.getMessage().contains("canvas"), e.getMessage());
    }

    @Test
    void testDumpWithUnknownOffset() throws Exception {
        final byte[] payload = { 7 };
        final WebPChunkIccp chunk = new WebPChunkIccp(fourCc("ICCP"), payload.length, payload);
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            chunk.dump(pw, -1);
        }
        assertTrue(out.toString().contains("unknown"), out.toString());
        assertTrue(out.toString().contains("ICCP"), out.toString());

        final StringWriter out2 = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out2)) {
            chunk.dump(pw, 128);
        }
        assertTrue(out2.toString().contains("128"), out2.toString());
    }
}
