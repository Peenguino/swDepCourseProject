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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.jpeg.JpegConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests the segment manipulation helpers of {@link JpegRewriter} on hand-built segment lists.
 */
class JpegRewriterSegmentsTest {

    private static final JpegRewriter REWRITER = new JpegRewriter();

    private static byte[] exifPayload() {
        final int size = JpegConstants.EXIF_IDENTIFIER_CODE.size();
        final byte[] data = new byte[size + 4];
        for (int i = 0; i < size; i++) {
            data[i] = JpegConstants.EXIF_IDENTIFIER_CODE.get(i);
        }
        return data;
    }

    private static JpegRewriter.JFIFPieceSegment segment(final int marker, final byte[] data) {
        return new JpegRewriter.JFIFPieceSegment(marker, data);
    }

    @Test
    void testSegmentClassification() {
        final JpegRewriter.JFIFPieceSegment exif = segment(JpegConstants.JPEG_APP1_MARKER, exifPayload());
        final JpegRewriter.JFIFPieceSegment app1Other = segment(JpegConstants.JPEG_APP1_MARKER, "other".getBytes(StandardCharsets.US_ASCII));
        final JpegRewriter.JFIFPieceSegment app2 = segment(JpegConstants.JPEG_APP2_MARKER, exifPayload());
        final JpegRewriter.JFIFPieceSegment sof = segment(JpegConstants.SOF0_MARKER, new byte[4]);

        assertTrue(exif.isApp1Segment());
        assertTrue(exif.isExifSegment());
        assertFalse(app1Other.isExifSegment(), "APP1 without Exif identifier");
        assertFalse(app2.isApp1Segment());
        assertFalse(app2.isExifSegment(), "Exif identifier in non-APP1 segment");
        assertTrue(app2.isAppSegment());
        assertFalse(sof.isAppSegment(), "SOF0 is above APP15");
        assertFalse(segment(0xFFD0, new byte[0]).isAppSegment(), "RST0 is below APP0");
        assertTrue(exif.toString().contains("0xffe1"));
    }

    @Test
    void testRemoveExifSegmentsKeepsOthers() {
        final JpegRewriter.JFIFPieceSegment exif = segment(JpegConstants.JPEG_APP1_MARKER, exifPayload());
        final JpegRewriter.JFIFPieceSegment app0 = segment(JpegConstants.JPEG_APP0_MARKER, new byte[] { 1 });

        final List<JpegRewriter.JFIFPieceSegment> result = REWRITER.removeExifSegments(Arrays.asList(app0, exif));

        assertEquals(1, result.size());
        assertSame(app0, result.get(0));
    }

    @Test
    void testInsertBeforeFirstAppSegment() throws ImagingException {
        final JpegRewriter.JFIFPiece image = new JpegRewriter.JFIFPieceImageData(new byte[] { (byte) 0xFF, (byte) 0xDA }, new byte[0]);
        final JpegRewriter.JFIFPieceSegment sof = segment(JpegConstants.SOF0_MARKER, new byte[4]);
        final JpegRewriter.JFIFPieceSegment app0 = segment(JpegConstants.JPEG_APP0_MARKER, new byte[] { 1 });
        final JpegRewriter.JFIFPieceSegment app1 = segment(JpegConstants.JPEG_APP1_MARKER, new byte[] { 2 });
        final JpegRewriter.JFIFPieceSegment inserted = segment(JpegConstants.JPEG_APP13_MARKER, new byte[] { 3 });

        final List<JpegRewriter.JFIFPiece> result = REWRITER.insertBeforeFirstAppSegments(Arrays.asList(image, sof, app0, app1),
                Collections.singletonList(inserted));

        assertEquals(Arrays.asList(image, sof, inserted, app0, app1), result);
        assertTrue(image.toString().contains("JFIFPieceImageData"));
    }

    @Test
    void testInsertBeforeFirstAppSegmentWithoutAppSegmentsFails() {
        final List<JpegRewriter.JFIFPieceSegment> noApp = Collections.singletonList(segment(JpegConstants.SOF0_MARKER, new byte[4]));
        final List<JpegRewriter.JFIFPieceSegment> inserted = Collections.singletonList(segment(JpegConstants.JPEG_APP1_MARKER, new byte[0]));

        assertThrows(ImagingException.class, () -> REWRITER.insertBeforeFirstAppSegments(noApp, inserted));
    }

    @Test
    void testInsertAfterLastAppSegmentHandlesEmptyAndAppLessLists() throws ImagingException {
        final List<JpegRewriter.JFIFPieceSegment> inserted = Collections.singletonList(segment(JpegConstants.JPEG_APP1_MARKER, new byte[0]));
        assertThrows(ImagingException.class,
                () -> REWRITER.insertAfterLastAppSegments(Collections.<JpegRewriter.JFIFPieceSegment>emptyList(), inserted));

        final JpegRewriter.JFIFPieceSegment sof = segment(JpegConstants.SOF0_MARKER, new byte[4]);
        final JpegRewriter.JFIFPieceSegment dqt = segment(JpegConstants.DQT_MARKER, new byte[4]);
        final List<JpegRewriter.JFIFPiece> result = REWRITER.insertAfterLastAppSegments(Arrays.asList(sof, dqt), inserted);
        assertEquals(Arrays.asList(sof, inserted.get(0), dqt), result);
    }
}
