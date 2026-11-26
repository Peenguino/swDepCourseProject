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
package org.apache.commons.imaging.formats.jpeg.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SegmentTypesTest {

    private static String segmentType(final int marker) {
        return new UnknownSegment(marker, new byte[0]).getSegmentType();
    }

    @Test
    void testGetSegmentTypeCoversAllMarkers() {
        for (int marker = 0xff01; marker <= 0xfffe; marker++) {
            assertNotNull(segmentType(marker), "no segment type for marker " + Integer.toHexString(marker));
        }
    }

    @Test
    void testGetSegmentTypeSpecificValues() {
        assertEquals("Start Of Frame, Baseline Dct, Huffman coding", segmentType(0xffc0));
        assertEquals("Start Of Frame, Progressive Dct, Huffman coding", segmentType(0xffc2));
        assertEquals("Start Of Frame, Reserved for JPEG extensions, arithmetic coding", segmentType(0xffc8));
        assertEquals("Define Huffman table(s)", segmentType(0xffc4));
        assertEquals("Define arithmetic coding conditioning(s)", segmentType(0xffcc));
        assertEquals("Restart with modulo 8 count 0", segmentType(0xffd0));
        assertEquals("Restart with modulo 8 count 7", segmentType(0xffd7));
        assertEquals("Start of image", segmentType(0xffd8));
        assertEquals("End of image", segmentType(0xffd9));
        assertEquals("Start of scan", segmentType(0xffda));
        assertEquals("Define quantization table(s)", segmentType(0xffdb));
        assertEquals("Define number of lines", segmentType(0xffdc));
        assertEquals("Define restart interval", segmentType(0xffdd));
        assertEquals("Define hierarchical progression", segmentType(0xffde));
        assertEquals("Expand reference component(s)", segmentType(0xffdf));
        assertEquals("Comment", segmentType(0xfffe));
        assertEquals("For temporary private use in arithmetic coding", segmentType(0xff01));
        assertEquals("Reserved", segmentType(0xff02));
        assertEquals("Reserved", segmentType(0xffbf));
        assertEquals("APP0", segmentType(0xffe0));
        assertEquals("APP15", segmentType(0xffef));
        assertEquals("JPG16", segmentType(0xfff0));
        assertEquals("JPG29", segmentType(0xfffd));
        assertEquals("Unknown", segmentType(0xffff));
        assertEquals("Unknown", segmentType(0xff00));
    }

    @Test
    void testUnknownSegmentDescriptionAndToString() {
        final UnknownSegment segment = new UnknownSegment(0xffc0, new byte[] { 1, 2 });
        assertEquals("Unknown (Start Of Frame, Baseline Dct, Huffman coding)", segment.getDescription());
        assertTrue(segment.toString().contains("Baseline"));
    }

    @Test
    void testComSegment() throws Exception {
        final ComSegment segment = new ComSegment(0xfffe, "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("COM (hello)", segment.getDescription());
        final StringWriter out = new StringWriter();
        try (PrintWriter pw = new PrintWriter(out)) {
            segment.dump(pw);
        }
        assertNotNull(out.toString());
    }

    @Test
    void testConcreteSegmentDescriptions() throws Exception {
        // Minimal valid payloads for each segment type.
        final byte[] dqt = new byte[65]; // one 8-bit quantization table
        assertTrue(new DqtSegment(0xffdb, dqt).getDescription().startsWith("DQT ("));

        final byte[] dht = new byte[17]; // one Huffman table with zero symbols
        assertTrue(new DhtSegment(0xffc4, dht).getDescription().startsWith("DHT ("));

        final byte[] sof = { 8, 0, 1, 0, 1, 1, 1, 0x11, 0 }; // 1x1 image, one component
        assertTrue(new SofnSegment(0xffc0, sof).getDescription().startsWith("SOFN (SOF0)"));

        final byte[] sos = { 1, 1, 0, 0, 0, 0 }; // one component, no spectral selection
        assertTrue(new SosSegment(0xffda, sos).getDescription().startsWith("SOS ("));

        final byte[] jfif = { 'J', 'F', 'I', 'F', 0, 1, 2, 0, 0, 1, 0, 1, 0, 0 };
        assertTrue(new JfifSegment(0xffe0, jfif).getDescription().startsWith("JFIF ("));
    }
}
