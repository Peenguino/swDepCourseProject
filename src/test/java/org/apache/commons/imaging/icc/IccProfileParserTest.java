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
package org.apache.commons.imaging.icc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IccProfileParserTest {

    private static byte[] quad(final String fourCc) {
        return fourCc.getBytes(StandardCharsets.US_ASCII);
    }

    /** Builds a minimal but structurally valid ICC profile with two tags. */
    private static byte[] buildProfile(final int deviceManufacturer, final int deviceModel) {
        final byte[] textData = { 't', 'e', 'x', 't', 0, 0, 0, 0, 'h', 'i' };
        final byte[] dataData = { 'd', 'a', 't', 'a', 0, 0, 0, 0 };
        final int tagTableEnd = 128 + 4 + 2 * 12;
        final int totalSize = tagTableEnd + textData.length + dataData.length;

        final ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(totalSize); // profile size
        buffer.put(quad("none")); // CMM type
        buffer.putInt(0x02100000); // version 2.1
        buffer.put(quad("mntr")); // device class
        buffer.put(quad("RGB ")); // color space
        buffer.put(quad("XYZ ")); // connection space
        buffer.put(new byte[12]); // creation date
        buffer.put(quad("acsp")); // profile file signature
        buffer.put(quad("MSFT")); // primary platform
        buffer.putInt(0); // flags
        buffer.putInt(deviceManufacturer);
        buffer.putInt(deviceModel);
        buffer.put(new byte[8]); // attributes
        buffer.putInt(0); // rendering intent
        buffer.put(new byte[12]); // illuminant
        buffer.put(quad("none")); // creator
        buffer.put(new byte[16]); // profile ID
        buffer.put(new byte[28]); // reserved
        buffer.putInt(2); // tag count

        buffer.put(quad("desc")); // known tag signature
        buffer.putInt(tagTableEnd);
        buffer.putInt(textData.length);
        buffer.put(quad("AAAA")); // unknown tag signature
        buffer.putInt(tagTableEnd + textData.length);
        buffer.putInt(dataData.length);

        buffer.put(textData);
        buffer.put(dataData);
        return buffer.array();
    }

    private static byte[] srgbProfile() {
        return buildProfile(IccConstants.IEC, IccConstants.sRGB);
    }

    @Test
    void testNullInputs() throws Exception {
        final IccProfileParser parser = new IccProfileParser();
        assertNull(parser.getIccProfileInfo((byte[]) null));
        assertNull(parser.getIccProfileInfo((File) null));
        assertNull(parser.getIccProfileInfo((java.awt.color.ICC_Profile) null));
    }

    @Test
    void testIsSrgb() throws Exception {
        final IccProfileParser parser = new IccProfileParser();
        assertTrue(parser.isSrgb(srgbProfile()));
        assertTrue(parser.isSrgb(ByteSource.array(srgbProfile())));
        assertFalse(parser.isSrgb(buildProfile(0x58585858, 0)));
    }

    @Test
    void testIsSrgbFromFile(@TempDir final File tempDir) throws Exception {
        final File file = new File(tempDir, "profile.icc");
        Files.write(file.toPath(), srgbProfile());
        assertTrue(new IccProfileParser().isSrgb(file));
    }

    @Test
    void testGetIccProfileInfoParsesTags() throws Exception {
        final byte[] profile = srgbProfile();
        final Logger logger = Logger.getLogger(IccProfileParser.class.getName());
        final Level previous = logger.getLevel();
        logger.setLevel(Level.FINEST);
        final IccProfileInfo info;
        try {
            info = new IccProfileParser().getIccProfileInfo(profile);
        } finally {
            logger.setLevel(previous);
        }

        assertNotNull(info);
        assertTrue(info.getData().length >= profile.length, "the cached data must cover the whole profile");
        assertEquals(2, info.getTags().length);
        assertTrue(info.isSrgb());
        assertEquals("desc", quadToString(info.getTags()[0].signature));
        assertEquals(10, info.getTags()[0].length);
        assertNull(info.getTags()[1].fIccTagType, "unknown tag signatures must map to null");
        assertNotNull(info.toString());
    }

    @Test
    void testGetIccProfileInfoRejectsTruncatedProfile() {
        // The declared profile size is much larger than the actual data.
        final byte[] profile = srgbProfile();
        final ByteBuffer header = ByteBuffer.wrap(profile).order(ByteOrder.BIG_ENDIAN);
        header.putInt(0, 100000);
        // Drop the tag table so no tag data is required before the size check.
        header.putInt(128, 0);
        assertThrows(ImagingException.class, () -> new IccProfileParser().getIccProfileInfo(profile));
    }

    @Test
    void testIccTagDumpWithoutData() throws Exception {
        final IccTag tag = new IccTag(0x64657363, 0, 0, null);
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            tag.dump(pw, "p: ");
        }
        assertTrue(out.toString().contains("data: null"), out.toString());
    }

    @Test
    void testIccTagDumpWithTextData() throws Exception {
        final IccTag tag = new IccTag(0x64657363, 0, 10, null);
        tag.setData(new byte[] { 't', 'e', 'x', 't', 0, 0, 0, 0, 'h', 'i' });
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            tag.dump(pw, "p: ");
        }
        assertTrue(out.toString().contains("textType"), out.toString());
        // Also exercise the logger-based dump.
        tag.dump("p: ");
    }

    @Test
    void testIccTagDumpWithUnknownDataType() throws Exception {
        final IccTag tag = new IccTag(0x64657363, 0, 4, null);
        tag.setData(new byte[] { 'X', 'Y', 'Z', ' ' });
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            tag.dump(pw, "p: ");
        }
        assertTrue(out.toString().contains("unknown"), out.toString());
    }

    @Test
    void testIccTagDataTypeDumps() throws Exception {
        // desc type: 12-byte header followed by a NUL-terminated ASCII string.
        final ByteArrayOutputStream desc = new ByteArrayOutputStream();
        desc.write(quad("desc"));
        desc.write(new byte[4]);
        desc.write(new byte[] { 0, 0, 0, 3 });
        desc.write(new byte[] { 'h', 'i', 0 });
        IccTagDataTypes.DESC_TYPE.dump("p: ", desc.toByteArray());

        IccTagDataTypes.DATA_TYPE.dump("p: ", quad("data"));
        IccTagDataTypes.MULTI_LOCALIZED_UNICODE_TYPE.dump("p: ", quad("mluc"));
        IccTagDataTypes.SIGNATURE_TYPE.dump("p: ", new byte[] { 's', 'i', 'g', ' ', 0, 0, 0, 0, 'I', 'E', 'C', ' ' });
        IccTagDataTypes.TEXT_TYPE.dump("p: ", new byte[] { 't', 'e', 'x', 't', 0, 0, 0, 0, 'h', 'i' });

        // Truncated desc data must fail.
        assertThrows(Exception.class, () -> IccTagDataTypes.DESC_TYPE.dump("p: ", new byte[] { 'd', 'e' }));
    }

    private static String quadToString(final int quad) {
        return new String(new byte[] { (byte) (quad >>> 24), (byte) (quad >> 16 & 0xff), (byte) (quad >> 8 & 0xff), (byte) quad },
                StandardCharsets.US_ASCII);
    }
}
