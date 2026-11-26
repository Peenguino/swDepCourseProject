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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks {@link JpegUtils#getMarkerName(int)} for every named marker and the behavior of {@link JpegImageMetadata} without EXIF data.
 */
class JpegUtilsAndMetadataTest {

    @ParameterizedTest
    @ValueSource(strings = { "SOS_MARKER", "JPEG_APP1_MARKER", "JPEG_APP2_MARKER", "JPEG_APP13_MARKER", "JPEG_APP14_MARKER", "JPEG_APP15_MARKER",
            "JFIF_MARKER", "SOF0_MARKER", "SOF1_MARKER", "SOF2_MARKER", "SOF3_MARKER", "SOF5_MARKER", "SOF6_MARKER", "SOF7_MARKER",
            "SOF8_MARKER", "SOF9_MARKER", "SOF10_MARKER", "SOF11_MARKER", "DAC_MARKER", "SOF13_MARKER", "SOF14_MARKER", "SOF15_MARKER", "DQT_MARKER",
            "DRI_MARKER", "RST0_MARKER", "RST1_MARKER", "RST2_MARKER", "RST3_MARKER", "RST4_MARKER", "RST5_MARKER", "RST6_MARKER", "RST7_MARKER" })
    void testMarkerNames(final String constantName) throws ReflectiveOperationException {
        final int marker = JpegConstants.class.getField(constantName).getInt(null);
        assertEquals(constantName, JpegUtils.getMarkerName(marker));
    }

    @Test
    void testUnknownMarkerName() {
        assertEquals("Unknown", JpegUtils.getMarkerName(0x1234));
    }

    @Test
    void testMetadataWithoutExif() throws IOException {
        final JpegPhotoshopMetadata photoshop = new JpegPhotoshopMetadata(new PhotoshopApp13Data(Collections.emptyList(), Collections.emptyList()));
        final JpegImageMetadata metadata = new JpegImageMetadata(photoshop, null);

        assertNull(metadata.getExif());
        assertNull(metadata.findExifValue(TiffTagConstants.TIFF_TAG_XRESOLUTION));
        assertNull(metadata.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_XRESOLUTION));
        assertNull(metadata.getExifThumbnail());
        assertNull(metadata.getExifThumbnailData());
        assertNull(metadata.getExifThumbnailSize());
        assertNull(metadata.getRawImageData());
        assertTrue(metadata.getItems().isEmpty());
        assertTrue(metadata.toString().contains("No Exif metadata."));
        assertEquals(photoshop, metadata.getPhotoshop());
    }
}
