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
package org.apache.commons.imaging.formats.jpeg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.BinaryConstant;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Builds header-only JPEG streams (SOI, APPn, SOFn, COM, EOI) to drive the color type, density and segment validation logic of
 * {@link JpegImageParser#getImageInfo} and related metadata readers, which never decode the entropy-coded data.
 */
class JpegSyntheticHeaderTest {

    private static final JpegImageParser PARSER = new JpegImageParser();

    private static byte[] segment(final int marker, final byte[] payload) {
        return ByteBuffer.allocate(4 + payload.length).putShort((short) marker).putShort((short) (payload.length + 2)).put(payload).array();
    }

    private static byte[] bytes(final BinaryConstant constant) {
        final byte[] result = new byte[constant.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = constant.get(i);
        }
        return result;
    }

    private static byte[] concat(final byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** SOFn segment; each component is {id, hSampling, vSampling}. */
    private static byte[] sof(final int marker, final int[]... components) {
        final ByteBuffer buf = ByteBuffer.allocate(6 + 3 * components.length).put((byte) 8).putShort((short) 20).putShort((short) 30)
                .put((byte) components.length);
        for (final int[] c : components) {
            buf.put((byte) c[0]).put((byte) (c[1] << 4 | c[2])).put((byte) 0);
        }
        return segment(marker, buf.array());
    }

    private static byte[] jfif(final int units, final int xDensity, final int yDensity) {
        final ByteBuffer buf = ByteBuffer.allocate(14).put("JFIF\0".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).put((byte) 2).put((byte) units)
                .putShort((short) xDensity).putShort((short) yDensity).put((byte) 0).put((byte) 0);
        return segment(JpegConstants.JPEG_APP0_MARKER, buf.array());
    }

    private static byte[] adobe(final int transform) {
        final ByteBuffer buf = ByteBuffer.allocate(12).put("Adobe".getBytes(StandardCharsets.US_ASCII)).putShort((short) 100).putShort((short) 0)
                .putShort((short) 0).put((byte) transform);
        return segment(JpegConstants.JPEG_APP14_MARKER, buf.array());
    }

    private static byte[] jpeg(final byte[]... segments) {
        return concat(new byte[] { (byte) 0xFF, (byte) 0xD8 }, concat(segments), new byte[] { (byte) 0xFF, (byte) 0xD9 });
    }

    private static ImageInfo info(final byte[] jpeg) throws IOException {
        return PARSER.getImageInfo(ByteSource.array(jpeg), null);
    }

    private static int[] c(final int id, final int h, final int v) {
        return new int[] { id, h, v };
    }

    static Stream<Arguments> colorTypes() {
        final int sof0 = JpegConstants.SOF0_MARKER;
        return Stream.of(
                // APP14 Adobe segment wins
                Arguments.of(jpeg(adobe(0), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1))), ImageInfo.ColorType.RGB, false),
                Arguments.of(jpeg(adobe(0), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1), c(4, 1, 1))), ImageInfo.ColorType.CMYK, false),
                Arguments.of(jpeg(adobe(0), sof(sof0, c(1, 1, 1))), ImageInfo.ColorType.UNKNOWN, false),
                Arguments.of(jpeg(adobe(1), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1))), ImageInfo.ColorType.YCbCr, false),
                Arguments.of(jpeg(adobe(2), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1), c(4, 1, 1))), ImageInfo.ColorType.YCCK, false),
                Arguments.of(jpeg(adobe(7), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1))), ImageInfo.ColorType.UNKNOWN, false),
                // then JFIF
                Arguments.of(jpeg(jfif(0, 1, 1), sof(sof0, c(1, 1, 1))), ImageInfo.ColorType.GRAYSCALE, false),
                Arguments.of(jpeg(jfif(0, 1, 1), sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1), c(4, 1, 1))), ImageInfo.ColorType.UNKNOWN, false),
                // then guessing from the component identifiers
                Arguments.of(jpeg(sof(sof0, c(1, 1, 1))), ImageInfo.ColorType.GRAYSCALE, false),
                Arguments.of(jpeg(sof(sof0, c(1, 1, 1), c(2, 1, 1))), ImageInfo.ColorType.GRAYSCALE, true),
                Arguments.of(jpeg(sof(sof0, c(1, 2, 2), c(2, 1, 1), c(3, 1, 1))), ImageInfo.ColorType.YCbCr, false),
                Arguments.of(jpeg(sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1), c(4, 1, 1))), ImageInfo.ColorType.YCbCr, true),
                Arguments.of(jpeg(sof(sof0, c('R', 1, 1), c('G', 1, 1), c('B', 1, 1))), ImageInfo.ColorType.RGB, false),
                Arguments.of(jpeg(sof(sof0, c('R', 1, 1), c('G', 1, 1), c('B', 1, 1), c('A', 1, 1))), ImageInfo.ColorType.RGB, true),
                Arguments.of(jpeg(sof(sof0, c('Y', 1, 1), c('C', 1, 1), c('c', 1, 1))), ImageInfo.ColorType.YCC, false),
                Arguments.of(jpeg(sof(sof0, c('Y', 1, 1), c('C', 1, 1), c('c', 1, 1), c('A', 1, 1))), ImageInfo.ColorType.YCC, true),
                Arguments.of(jpeg(sof(sof0, c(7, 1, 1), c(8, 1, 1), c(9, 1, 1))), ImageInfo.ColorType.RGB, false),
                Arguments.of(jpeg(sof(sof0, c(7, 2, 1), c(8, 1, 1), c(9, 1, 1))), ImageInfo.ColorType.YCbCr, false),
                Arguments.of(jpeg(sof(sof0, c(7, 1, 1), c(8, 1, 2), c(9, 1, 1))), ImageInfo.ColorType.YCbCr, false),
                Arguments.of(jpeg(sof(sof0, c(7, 1, 1), c(8, 1, 1), c(9, 1, 1), c(10, 1, 1))), ImageInfo.ColorType.CMYK, false),
                Arguments.of(jpeg(sof(sof0, c(7, 2, 2), c(8, 1, 1), c(9, 1, 1), c(10, 2, 2))), ImageInfo.ColorType.YCCK, false),
                Arguments.of(jpeg(sof(sof0, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1), c(4, 1, 1), c(5, 1, 1))), ImageInfo.ColorType.UNKNOWN, false));
    }

    @ParameterizedTest
    @MethodSource("colorTypes")
    void testColorTypeDetection(final byte[] jpeg, final ImageInfo.ColorType expected, final boolean transparent) throws IOException {
        final ImageInfo info = info(jpeg);
        assertEquals(expected, info.getColorType());
        assertEquals(transparent, info.isTransparent());
        assertEquals(30, info.getWidth());
        assertEquals(20, info.getHeight());
    }

    @Test
    void testJfifDensityUnits() throws IOException {
        final int[] y = { 1, 1, 1 };
        assertEquals(-1, info(jpeg(jfif(0, 72, 72), sof(JpegConstants.SOF0_MARKER, y))).getPhysicalWidthDpi());
        assertEquals(72, info(jpeg(jfif(1, 72, 96), sof(JpegConstants.SOF0_MARKER, y))).getPhysicalWidthDpi());
        assertEquals(96, info(jpeg(jfif(1, 72, 96), sof(JpegConstants.SOF0_MARKER, y))).getPhysicalHeightDpi());
        assertEquals(254, info(jpeg(jfif(2, 100, 100), sof(JpegConstants.SOF0_MARKER, y))).getPhysicalWidthDpi());
        assertEquals(-1, info(jpeg(jfif(9, 100, 100), sof(JpegConstants.SOF0_MARKER, y))).getPhysicalWidthDpi());
        assertEquals("Jpeg/JFIF v.1.2", info(jpeg(jfif(0, 1, 1), sof(JpegConstants.SOF0_MARKER, y))).getFormatDetails());
    }

    private static byte[] exif(final int unit, final int xRes) throws IOException {
        final TiffOutputSet set = new TiffOutputSet(ByteOrder.BIG_ENDIAN);
        final TiffOutputDirectory root = set.getOrCreateRootDirectory();
        root.add(TiffTagConstants.TIFF_TAG_XRESOLUTION, new RationalNumber(xRes, 1));
        root.add(TiffTagConstants.TIFF_TAG_YRESOLUTION, new RationalNumber(xRes, 1));
        root.add(TiffTagConstants.TIFF_TAG_RESOLUTION_UNIT, (short) unit);
        final ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        new TiffImageWriterLossy(ByteOrder.BIG_ENDIAN).write(tiff, set);
        return segment(JpegConstants.JPEG_APP1_MARKER, concat(bytes(JpegConstants.EXIF_IDENTIFIER_CODE), new byte[] { 0, 0 }, tiff.toByteArray()));
    }

    @Test
    void testExifDensityWhenNoJfif() throws IOException {
        final byte[] sof = sof(JpegConstants.SOF0_MARKER, c(1, 1, 1));
        assertEquals(300, info(jpeg(exif(2, 300), sof)).getPhysicalWidthDpi());
        assertEquals(254, info(jpeg(exif(3, 100), sof)).getPhysicalWidthDpi());
        assertEquals(-1, info(jpeg(exif(1, 100), sof)).getPhysicalWidthDpi());
        assertEquals(-1, info(jpeg(exif(5, 100), sof)).getPhysicalWidthDpi());
        assertEquals("Jpeg/DCM", info(jpeg(exif(2, 300), sof)).getFormatDetails());

        final ImagingException twoExif = assertThrows(ImagingException.class,
                () -> PARSER.getExifMetadata(ByteSource.array(jpeg(exif(2, 1), exif(2, 1), sof)), null));
        assertTrue(twoExif.getMessage().contains("multiple APP1 segments"), twoExif.getMessage());
    }

    @Test
    void testProgressiveAndComments() throws IOException {
        final byte[] com = segment(JpegConstants.COM_MARKER, "hello".getBytes(StandardCharsets.UTF_8));
        final ImageInfo info = info(jpeg(com, sof(JpegConstants.SOF2_MARKER, c(1, 1, 1))));
        assertTrue(info.isProgressive());
        assertEquals(1, info.getComments().size());
        assertEquals("hello", info.getComments().get(0));
        assertFalse(info(jpeg(sof(JpegConstants.SOF1_MARKER, c(1, 1, 1)))).isProgressive());
    }

    private static byte[] icc(final int cur, final int count, final byte... data) {
        return segment(JpegConstants.JPEG_APP2_MARKER, concat(bytes(JpegConstants.ICC_PROFILE_LABEL), new byte[] { (byte) cur, (byte) count }, data));
    }

    @Test
    void testIccProfileSegmentsAssembly() throws IOException {
        final byte[] sof = sof(JpegConstants.SOF0_MARKER, c(1, 1, 1));
        // out of order but complete, numbered from 1
        assertArrayEquals(new byte[] { 1, 2, 3 }, PARSER.getIccProfileBytes(ByteSource.array(jpeg(icc(2, 2, (byte) 3), icc(1, 2, (byte) 1, (byte) 2), sof)), null));
        // numbered from 0: first attempt fails, second one starting with zero succeeds
        assertArrayEquals(new byte[] { 1, 2 }, PARSER.getIccProfileBytes(ByteSource.array(jpeg(icc(0, 2, (byte) 1), icc(1, 2, (byte) 2), sof)), null));

        final ByteSource missing = ByteSource.array(jpeg(icc(1, 3, (byte) 1), icc(2, 3, (byte) 2), sof));
        assertTrue(assertThrows(ImagingException.class, () -> PARSER.getIccProfileBytes(missing, null)).getMessage().startsWith("App2 Segments Missing"));
        final ByteSource incoherent = ByteSource.array(jpeg(icc(1, 2, (byte) 1), icc(5, 2, (byte) 2), sof));
        assertTrue(assertThrows(ImagingException.class, () -> PARSER.getIccProfileBytes(incoherent, null)).getMessage().startsWith("Incoherent"));
        final ByteSource inconsistent = ByteSource.array(jpeg(icc(1, 2, (byte) 1), icc(2, 3, (byte) 2), sof));
        // the count mismatch is detected first, then the zero-based retry reports the ordering problem
        assertThrows(ImagingException.class, () -> PARSER.getIccProfileBytes(inconsistent, null));
    }

    @Test
    void testDuplicateXmpSegments() throws IOException {
        final byte[] sof = sof(JpegConstants.SOF0_MARKER, c(1, 1, 1));
        final byte[] xmp = segment(JpegConstants.JPEG_APP1_MARKER, concat(bytes(JpegConstants.XMP_IDENTIFIER), "<x/>".getBytes(StandardCharsets.UTF_8)));
        final byte[] otherXmp = segment(JpegConstants.JPEG_APP1_MARKER, concat(bytes(JpegConstants.XMP_IDENTIFIER), "<y/>".getBytes(StandardCharsets.UTF_8)));
        // traversal stops at the first XMP segment
        assertEquals("<x/>", PARSER.getXmpXml(ByteSource.array(jpeg(xmp, otherXmp, sof)), null));

    }

    @Test
    void testDumpAndSimpleAccessors() throws IOException {
        final byte[] jpeg = jpeg(jfif(1, 72, 72), adobe(1), sof(JpegConstants.SOF0_MARKER, c(1, 1, 1), c(2, 1, 1), c(3, 1, 1)));
        final StringWriter sw = new StringWriter();
        assertTrue(PARSER.dumpImageFile(new PrintWriter(sw), ByteSource.array(jpeg)));
        assertTrue(sw.toString().contains("Color Type: YCbCr"), sw.toString());
        assertEquals("jpg", PARSER.getDefaultExtension());
        assertNotNull(PARSER.getDefaultParameters());
        assertEquals("Jpeg-Custom", PARSER.getName());

        final ImagingException noSof = assertThrows(ImagingException.class, () -> PARSER.getImageSize(ByteSource.array(jpeg(jfif(1, 1, 1))), null));
        assertEquals("No JFIF Data Found.", noSof.getMessage());
    }
}
