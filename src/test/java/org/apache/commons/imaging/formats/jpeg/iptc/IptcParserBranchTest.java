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
package org.apache.commons.imaging.formats.jpeg.iptc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.imaging.ImagingException;
import org.junit.jupiter.api.Test;

class IptcParserBranchTest {

    private static final byte[] PHOTOSHOP_PREFIX = "Photoshop 3.0\0".getBytes(StandardCharsets.ISO_8859_1);

    private static byte[] record(final int recordNumber, final int recordType, final byte[] data) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(0x1c);
        os.write(recordNumber);
        os.write(recordType);
        os.write(data.length >> 8 & 0xff);
        os.write(data.length & 0xff);
        os.write(data, 0, data.length);
        return os.toByteArray();
    }

    private static byte[] concat(final byte[]... arrays) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (final byte[] array : arrays) {
            os.write(array, 0, array.length);
        }
        return os.toByteArray();
    }

    private static byte[] block(final int type, final byte[] name, final byte[] data) throws java.io.IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(new byte[] { '8', 'B', 'I', 'M' });
        os.write(type >> 8 & 0xff);
        os.write(type & 0xff);
        os.write(name.length);
        os.write(name, 0, name.length);
        if (name.length == 0) {
            os.write(0); // empty-name padding byte
        } else if (name.length % 2 == 0) {
            os.write(0); // even-length name padding byte
        }
        os.write(data.length >> 24 & 0xff);
        os.write(data.length >> 16 & 0xff);
        os.write(data.length >> 8 & 0xff);
        os.write(data.length & 0xff);
        os.write(data, 0, data.length);
        if (data.length % 2 != 0) {
            os.write(0);
        }
        return os.toByteArray();
    }

    @Test
    void testIsPhotoshopJpegSegment() {
        final IptcParser parser = new IptcParser();
        assertFalse(parser.isPhotoshopJpegSegment(new byte[0]));
        assertFalse(parser.isPhotoshopJpegSegment(PHOTOSHOP_PREFIX), "prefix without block signature must be rejected");
        assertFalse(parser.isPhotoshopJpegSegment(concat(PHOTOSHOP_PREFIX, "XXXX".getBytes(StandardCharsets.US_ASCII))));
        assertTrue(parser.isPhotoshopJpegSegment(concat(PHOTOSHOP_PREFIX, "8BIM".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void testParseIptcBlockWithCharset() {
        final IptcParser parser = new IptcParser();
        final byte[] charsetRecord = record(1, 90, "UTF-8".getBytes(StandardCharsets.ISO_8859_1));
        final byte[] textRecord = record(2, 5, "café".getBytes(StandardCharsets.UTF_8));
        final List<IptcRecord> records = parser.parseIptcBlock(concat(charsetRecord, textRecord));
        assertEquals(1, records.size());
        assertEquals("café", records.get(0).getValue());
    }

    @Test
    void testParseIptcBlockWithEscapeSequenceCharset() {
        final IptcParser parser = new IptcParser();
        final byte[] charsetRecord = record(1, 90, new byte[] { 0x1b, '%', 'G' });
        final byte[] textRecord = record(2, 5, "café".getBytes(StandardCharsets.UTF_8));
        final List<IptcRecord> records = parser.parseIptcBlock(concat(charsetRecord, textRecord));
        assertEquals("café", records.get(0).getValue());
    }

    @Test
    void testParseIptcBlockWithUnsupportedCharset() {
        final IptcParser parser = new IptcParser();
        final byte[] charsetRecord = record(1, 90, "NOT-A-CHARSET".getBytes(StandardCharsets.ISO_8859_1));
        final byte[] textRecord = record(2, 5, "hi".getBytes(StandardCharsets.ISO_8859_1));
        final List<IptcRecord> records = parser.parseIptcBlock(concat(charsetRecord, textRecord));
        assertEquals("hi", records.get(0).getValue());
    }

    @Test
    void testParseIptcBlockSkipsNonApplicationRecords() {
        final IptcParser parser = new IptcParser();
        final byte[] envelope = record(1, 1, "x".getBytes(StandardCharsets.ISO_8859_1));
        final byte[] version = record(2, 0, new byte[] { 0, 2 });
        final byte[] textRecord = record(2, 5, "hi".getBytes(StandardCharsets.ISO_8859_1));
        final List<IptcRecord> records = parser.parseIptcBlock(concat(envelope, version, textRecord));
        assertEquals(1, records.size());
        assertEquals("hi", records.get(0).getValue());
    }

    @Test
    void testParseIptcBlockStopsAtUnexpectedMarker() {
        final IptcParser parser = new IptcParser();
        final List<IptcRecord> records = parser.parseIptcBlock(new byte[] { 0x00, 0x02, 0x05 });
        assertTrue(records.isEmpty());
    }

    @Test
    void testParseIptcBlockStopsAtExtendedDataset() {
        final IptcParser parser = new IptcParser();
        // Record size 0x8000 exceeds the non-extended maximum: parsing stops.
        final byte[] extended = { 0x1c, 0x02, 0x05, (byte) 0x80, 0x00 };
        assertTrue(parser.parseIptcBlock(extended).isEmpty());
    }

    @Test
    void testParseAllBlocksReadsValidBlock() throws Exception {
        final IptcParser parser = new IptcParser();
        final byte[] iptcData = record(2, 5, "hi".getBytes(StandardCharsets.ISO_8859_1));
        final byte[] bytes = concat(PHOTOSHOP_PREFIX, block(0x0404, new byte[0], iptcData));
        final List<IptcBlock> blocks = parser.parseAllBlocks(bytes, true);
        assertEquals(1, blocks.size());
        assertEquals(0x0404, blocks.get(0).getBlockType());
    }

    @Test
    void testParseAllBlocksWithNamedBlock() throws Exception {
        final IptcParser parser = new IptcParser();
        // Even-length name exercises the name padding byte.
        final byte[] bytes = concat(PHOTOSHOP_PREFIX, block(0x0404, "ab".getBytes(StandardCharsets.US_ASCII), new byte[] { 1, 2 }));
        final List<IptcBlock> blocks = parser.parseAllBlocks(bytes, true);
        assertEquals(1, blocks.size());
    }

    @Test
    void testParseAllBlocksSkipsIgnoredBlockTypes() throws Exception {
        final IptcParser parser = new IptcParser();
        // An ignored block (type 1084) as the last block: skipped, no blocks returned.
        final byte[] ignored = { '8', 'B', 'I', 'M', 0x04, 0x3c, 0, 0 };
        final List<IptcBlock> blocks = parser.parseAllBlocks(concat(PHOTOSHOP_PREFIX, ignored), true);
        assertTrue(blocks.isEmpty());
    }

    @Test
    void testParseAllBlocksIgnoredBlockSkipIsBrokenWhenABlockFollows() throws Exception {
        // NOTE: searchQuad() consumes the next block's 8BIM signature, so the signature
        // re-read after skipping an ignored block fails. Documents the existing (buggy) behavior.
        final IptcParser parser = new IptcParser();
        final byte[] ignored = { '8', 'B', 'I', 'M', 0x04, 0x3c, 0, 0 };
        final byte[] valid = block(0x0404, new byte[0], new byte[] { 1, 2 });
        final ImagingException e = assertThrows(ImagingException.class, () -> parser.parseAllBlocks(concat(PHOTOSHOP_PREFIX, ignored, valid), true));
        assertTrue(e.getMessage().contains("Invalid Image Resource Block Signature"), e.getMessage());
    }

    @Test
    void testParseAllBlocksRejectsWrongIdentification() {
        final IptcParser parser = new IptcParser();
        final ImagingException e = assertThrows(ImagingException.class,
                () -> parser.parseAllBlocks("Not Photoshop at all".getBytes(StandardCharsets.ISO_8859_1), true));
        assertTrue(e.getMessage().contains("Not a Photoshop App13 Segment"), e.getMessage());
    }

    @Test
    void testParseAllBlocksRejectsInvalidSignature() {
        final IptcParser parser = new IptcParser();
        final byte[] bytes = concat(PHOTOSHOP_PREFIX, "XXXX".getBytes(StandardCharsets.US_ASCII));
        final ImagingException e = assertThrows(ImagingException.class, () -> parser.parseAllBlocks(bytes, true));
        assertTrue(e.getMessage().contains("Invalid Image Resource Block Signature"), e.getMessage());
    }

    @Test
    void testParseAllBlocksRejectsOversizedBlock() {
        final IptcParser parser = new IptcParser();
        final byte[] header = { '8', 'B', 'I', 'M', 0x04, 0x04, 0, 0, 0, 0, 0, 0x7f };
        final byte[] bytes = concat(PHOTOSHOP_PREFIX, header);
        final ImagingException e = assertThrows(ImagingException.class, () -> parser.parseAllBlocks(bytes, true));
        assertTrue(e.getMessage().contains("Invalid Block Size"), e.getMessage());
    }

    @Test
    void testParseAllBlocksTruncatedNameStrictVsLenient() throws Exception {
        final IptcParser parser = new IptcParser();
        // Name length 10 but no name bytes available.
        final byte[] truncated = { '8', 'B', 'I', 'M', 0x04, 0x04, 10 };
        final byte[] bytes = concat(PHOTOSHOP_PREFIX, truncated);
        assertThrows(Exception.class, () -> parser.parseAllBlocks(bytes, true));
        assertTrue(parser.parseAllBlocks(bytes, false).isEmpty(), "lenient parsing must stop without errors");
    }

    @Test
    void testGetUnknownIptcType() {
        final IptcType unknown = IptcTypes.getUnknown(999);
        assertEquals("Unknown", unknown.getName());
        assertEquals(999, unknown.getType());
        assertEquals("Unknown (999)", unknown.toString());
    }
}
