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
package org.apache.commons.imaging.formats.jpeg.xmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.commons.imaging.test.TestResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JpegXmpRewriterVariantsTest {

    private static final String XMP = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF/></x:xmpmeta>";

    private static byte[] jpegBytes() throws Exception {
        final File file = TestResources.resourceToFile("/data/images/jpg/1/Oregon Scientific DS6639 - DSC_0307 - small.jpg");
        return Files.readAllBytes(file.toPath());
    }

    private static String xmpOf(final byte[] jpeg) throws Exception {
        return new JpegImageParser().getXmpXml(ByteSource.array(jpeg), null);
    }

    @Test
    void testUpdateXmpXmlFromBytes() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().updateXmpXml(jpegBytes(), os, XMP);
        final byte[] updated = os.toByteArray();
        assertTrue(updated.length > 2);
        assertEquals(0xff, updated[0] & 0xff);
        assertEquals(0xd8, updated[1] & 0xff);
        assertEquals(XMP, xmpOf(updated));
    }

    @Test
    void testUpdateXmpXmlFromInputStream() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().updateXmpXml(new ByteArrayInputStream(jpegBytes()), os, XMP);
        assertEquals(XMP, xmpOf(os.toByteArray()));
    }

    @Test
    void testUpdateXmpXmlFromFile(@TempDir final File tempDir) throws Exception {
        final File src = new File(tempDir, "src.jpg");
        Files.write(src.toPath(), jpegBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().updateXmpXml(src, os, XMP);
        assertEquals(XMP, xmpOf(os.toByteArray()));
    }

    @Test
    void testRemoveXmpXmlFromBytes() throws Exception {
        final ByteArrayOutputStream updated = new ByteArrayOutputStream();
        new JpegXmpRewriter().updateXmpXml(jpegBytes(), updated, XMP);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().removeXmpXml(updated.toByteArray(), os);
        assertNull(xmpOf(os.toByteArray()), "XMP must be gone after removal");
    }

    @Test
    void testRemoveXmpXmlFromInputStream() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().removeXmpXml(new ByteArrayInputStream(jpegBytes()), os);
        assertNull(xmpOf(os.toByteArray()));
    }

    @Test
    void testRemoveXmpXmlFromFile(@TempDir final File tempDir) throws Exception {
        final File src = new File(tempDir, "src.jpg");
        Files.write(src.toPath(), jpegBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().removeXmpXml(src, os);
        assertNull(xmpOf(os.toByteArray()));
    }

    @Test
    void testUpdateXmpXmlWithoutAppSegments() throws Exception {
        // Minimal JPEG: SOI + EOI and no APP segments.
        // NOTE: insertAfterLastAppSegments() appends the new segment at index 1 of the piece
        // list, which for such files puts the XMP segment AFTER the EOI marker. The XMP is
        // therefore not found when reading back. Documents the existing (buggy) behavior.
        final byte[] minimal = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9 };
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JpegXmpRewriter().updateXmpXml(minimal, os, XMP);
        final byte[] updated = os.toByteArray();
        assertEquals(0xd9, updated[3] & 0xff, "EOI currently precedes the inserted XMP segment");
        assertNull(xmpOf(updated));
    }
}
