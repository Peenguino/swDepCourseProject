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
package org.apache.commons.imaging.formats.tiff.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TiffPlanarConfigurationTest {

    @Test
    void testLenientValueOfChunky() {
        assertEquals(TiffPlanarConfiguration.CHUNKY,
                TiffPlanarConfiguration.lenientValueOf(TiffTagConstants.PLANAR_CONFIGURATION_VALUE_CHUNKY));
    }

    @Test
    void testLenientValueOfPlanar() {
        assertEquals(TiffPlanarConfiguration.PLANAR,
                TiffPlanarConfiguration.lenientValueOf(TiffTagConstants.PLANAR_CONFIGURATION_VALUE_PLANAR));
    }

    @Test
    void testLenientValueOfUnknownCodeFallsBackToChunky() {
        assertEquals(TiffPlanarConfiguration.CHUNKY, TiffPlanarConfiguration.lenientValueOf(0));
        assertEquals(TiffPlanarConfiguration.CHUNKY, TiffPlanarConfiguration.lenientValueOf(42));
        assertEquals(TiffPlanarConfiguration.CHUNKY, TiffPlanarConfiguration.lenientValueOf(-1));
    }

    @Test
    void testCodeValues() {
        assertEquals(TiffTagConstants.PLANAR_CONFIGURATION_VALUE_CHUNKY, TiffPlanarConfiguration.CHUNKY.codeValue);
        assertEquals(TiffTagConstants.PLANAR_CONFIGURATION_VALUE_PLANAR, TiffPlanarConfiguration.PLANAR.codeValue);
    }
}
