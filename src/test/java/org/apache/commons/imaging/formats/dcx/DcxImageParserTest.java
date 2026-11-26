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
package org.apache.commons.imaging.formats.dcx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.pcx.PcxImagingParameters;
import org.junit.jupiter.api.Test;

class DcxImageParserTest {

    private static final int DCX_ID = 0x3ADE68B1;
    private static final int HEADER_SIZE = 4 + 1024 * 4;

    private static byte[] dcxHeader(final int id, final int firstPageOffset, final boolean terminateTable) {
        final ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(id);
        buffer.putInt(firstPageOffset);
        for (int i = 1; i < 1024; i++) {
            buffer.putInt(terminateTable ? 0 : firstPageOffset);
        }
        return buffer.array();
    }

    @Test
    void testWriteThenReadRoundTrip() throws Exception {
        final BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xff0000);
        src.setRGB(1, 0, 0x00ff00);
        src.setRGB(0, 1, 0x0000ff);
        src.setRGB(1, 1, 0xffffff);

        final DcxImageParser parser = new DcxImageParser();
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        parser.writeImage(src, os, new PcxImagingParameters());
        final byte[] bytes = os.toByteArray();
        assertTrue(bytes.length > HEADER_SIZE, "a DCX file must contain the header plus a PCX page");

        final List<BufferedImage> images = parser.getAllBufferedImages(ByteSource.array(bytes));
        assertEquals(1, images.size());
        final BufferedImage readBack = images.get(0);
        assertEquals(2, readBack.getWidth());
        assertEquals(2, readBack.getHeight());
        assertEquals(0xffff0000, readBack.getRGB(0, 0));
        assertEquals(0xffffffff, readBack.getRGB(1, 1));

        assertNotNull(parser.getBufferedImage(ByteSource.array(bytes), new PcxImagingParameters()));
    }

    @Test
    void testDumpImageFilePrintsHeader() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        new DcxImageParser().writeImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), os, new PcxImagingParameters());

        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            assertTrue(new DcxImageParser().dumpImageFile(pw, ByteSource.array(os.toByteArray())));
        }
        final String dump = out.toString();
        assertTrue(dump.contains("DcxHeader"), dump);
        assertTrue(dump.contains("Id: 0x" + Integer.toHexString(DCX_ID)), dump);
        assertTrue(dump.contains("Pages: 1"), dump);
    }

    @Test
    void testEmptyPageTableYieldsNoImages() throws Exception {
        final byte[] bytes = dcxHeader(DCX_ID, 0, true);
        final DcxImageParser parser = new DcxImageParser();
        assertTrue(parser.getAllBufferedImages(ByteSource.array(bytes)).isEmpty());
        assertNull(parser.getBufferedImage(ByteSource.array(bytes), new PcxImagingParameters()));
    }

    @Test
    void testInvalidIdIsRejected() {
        final byte[] bytes = dcxHeader(0, 0, true);
        final DcxImageParser parser = new DcxImageParser();
        final ImagingException e = assertThrows(ImagingException.class, () -> parser.getAllBufferedImages(ByteSource.array(bytes)));
        assertTrue(e.getMessage().contains("Not a Valid DCX File"), e.getMessage());
    }

    @Test
    void testUnterminatedPageTableIsRejected() {
        final byte[] bytes = dcxHeader(DCX_ID, HEADER_SIZE, false);
        final DcxImageParser parser = new DcxImageParser();
        final ImagingException e = assertThrows(ImagingException.class, () -> parser.getAllBufferedImages(ByteSource.array(bytes)));
        assertTrue(e.getMessage().contains("page table not terminated"), e.getMessage());
    }

    @Test
    void testTruncatedFileIsRejected() {
        final byte[] bytes = new byte[8];
        final DcxImageParser parser = new DcxImageParser();
        assertThrows(Exception.class, () -> parser.getAllBufferedImages(ByteSource.array(bytes)));
    }

    @Test
    void testNameAndExtension() {
        final DcxImageParser parser = new DcxImageParser();
        assertEquals("Dcx-Custom", parser.getName());
        assertEquals("dcx", parser.getDefaultExtension());
        assertNotNull(parser.getDefaultParameters());
    }
}
