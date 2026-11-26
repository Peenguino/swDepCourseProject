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
package org.apache.commons.imaging.formats.pnm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

class PnmParserErrorTest {

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static BufferedImage read(final byte[] bytes) throws Exception {
        return new PnmImageParser().getBufferedImage(ByteSource.array(bytes), new PnmImagingParameters());
    }

    private static ImagingException readFailure(final byte[] bytes) {
        return assertThrows(ImagingException.class, () -> read(bytes));
    }

    @Test
    void testInvalidPrefixByte1() {
        final ImagingException e = readFailure(ascii("X1\n1 1\n0\n"));
        assertTrue(e.getMessage().contains("prefix byte 1"), e.getMessage());
    }

    @Test
    void testInvalidPrefixByte2() {
        final ImagingException e = readFailure(ascii("P9\n1 1\n0\n"));
        assertTrue(e.getMessage().contains("prefix byte 2"), e.getMessage());
    }

    @Test
    void testPbmTextInvalidBit() {
        final ImagingException e = readFailure(ascii("P1\n1 1\n2\n"));
        assertTrue(e.getMessage().contains("bad bit"), e.getMessage());
    }

    @Test
    void testPbmRawUnexpectedEof() {
        final ImagingException e = readFailure(ascii("P4\n2 1\n"));
        assertTrue(e.getMessage().contains("Unexpected EOF"), e.getMessage());
    }

    @Test
    void testPpmRawUnexpectedEof() {
        final ImagingException e = readFailure(ascii("P6\n1 1\n255\n"));
        assertTrue(e.getMessage().contains("Unexpected EOF"), e.getMessage());
    }

    @Test
    void testPpmNegativeSample() {
        final ImagingException e = readFailure(ascii("P3\n1 1\n255\n-1 0 0\n"));
        assertTrue(e.getMessage().contains("Negative pixel values"), e.getMessage());
    }

    @Test
    void testPpmSampleAboveMaxBecomesBlack() throws Exception {
        final BufferedImage image = read(ascii("P3\n1 1\n255\n300 0 0\n"));
        assertEquals(0xff000000, image.getRGB(0, 0));
    }

    @Test
    void testPpmMaxValZero() {
        final ImagingException e = readFailure(ascii("P6\n1 1\n0\n"));
        assertTrue(e.getMessage().contains("out of range"), e.getMessage());
    }

    @Test
    void testPpmMaxValTooLarge() {
        final ImagingException e = readFailure(ascii("P6\n1 1\n70000\n"));
        assertTrue(e.getMessage().contains("out of range"), e.getMessage());
    }

    @Test
    void testPamMaxValZero() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nDEPTH 1\nMAXVAL 0\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("out of range"), e.getMessage());
    }

    @Test
    void testPamMaxValTooLarge() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nDEPTH 1\nMAXVAL 70000\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("out of range"), e.getMessage());
    }

    @Test
    void testPamUnknownTupleType() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nDEPTH 3\nMAXVAL 255\nTUPLTYPE CMYK\nENDHDR\n"));
        assertTrue(e.getMessage().contains("Unknown PAM tupletype"), e.getMessage());
    }

    @Test
    void testPamMissingWidth() {
        final ImagingException e = readFailure(ascii("P7\nHEIGHT 1\nDEPTH 1\nMAXVAL 255\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no WIDTH value"), e.getMessage());
    }

    @Test
    void testPamMissingHeight() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nDEPTH 1\nMAXVAL 255\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no HEIGHT value"), e.getMessage());
    }

    @Test
    void testPamMissingDepth() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nMAXVAL 255\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no DEPTH value"), e.getMessage());
    }

    @Test
    void testPamMissingMaxVal() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nDEPTH 1\nTUPLTYPE GRAYSCALE\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no MAXVAL value"), e.getMessage());
    }

    @Test
    void testPamMissingTupleType() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH 1\nHEIGHT 1\nDEPTH 1\nMAXVAL 255\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no TUPLTYPE value"), e.getMessage());
    }

    @Test
    void testPamInvalidHeaderType() {
        final ImagingException e = readFailure(ascii("P7\nFOO 1\nENDHDR\n"));
        assertTrue(e.getMessage().contains("Invalid PAM file header type"), e.getMessage());
    }

    @Test
    void testPamMissingValueAfterKey() {
        final ImagingException e = readFailure(ascii("P7\nWIDTH\nENDHDR\n"));
        assertTrue(e.getMessage().contains("no WIDTH value"), e.getMessage());
    }
}
