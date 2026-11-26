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
package org.apache.commons.imaging.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ColorValueObjectsTest {

    private static void checkValueObject(final Object a, final Object same, final Object... notEquals) {
        assertEquals(a, a, "an instance must equal itself");
        assertEquals(a, same, "instances with equal components must be equal");
        assertEquals(a.hashCode(), same.hashCode(), "equal instances must share the hash code");
        assertFalse(a.equals(null), "null must not be equal");
        assertFalse(a.equals("not a color"), "instances of other classes must not be equal");
        for (final Object other : notEquals) {
            assertNotEquals(a, other, "instances with different components must not be equal");
        }
        assertNotNull(a.toString());
    }

    @Test
    void testColorCieLab() {
        checkValueObject(new ColorCieLab(1, 2, 3), new ColorCieLab(1, 2, 3),
                new ColorCieLab(9, 2, 3), new ColorCieLab(1, 9, 3), new ColorCieLab(1, 2, 9));
    }

    @Test
    void testColorCieLch() {
        checkValueObject(new ColorCieLch(1, 2, 3), new ColorCieLch(1, 2, 3),
                new ColorCieLch(9, 2, 3), new ColorCieLch(1, 9, 3), new ColorCieLch(1, 2, 9));
    }

    @Test
    void testColorCieLuv() {
        checkValueObject(new ColorCieLuv(1, 2, 3), new ColorCieLuv(1, 2, 3),
                new ColorCieLuv(9, 2, 3), new ColorCieLuv(1, 9, 3), new ColorCieLuv(1, 2, 9));
    }

    @Test
    void testColorCmy() {
        checkValueObject(new ColorCmy(1, 2, 3), new ColorCmy(1, 2, 3),
                new ColorCmy(9, 2, 3), new ColorCmy(1, 9, 3), new ColorCmy(1, 2, 9));
    }

    @Test
    void testColorCmyk() {
        checkValueObject(new ColorCmyk(1, 2, 3, 4), new ColorCmyk(1, 2, 3, 4),
                new ColorCmyk(9, 2, 3, 4), new ColorCmyk(1, 9, 3, 4), new ColorCmyk(1, 2, 9, 4), new ColorCmyk(1, 2, 3, 9));
    }

    @Test
    void testColorDin99Lab() {
        checkValueObject(new ColorDin99Lab(1, 2, 3), new ColorDin99Lab(1, 2, 3),
                new ColorDin99Lab(9, 2, 3), new ColorDin99Lab(1, 9, 3), new ColorDin99Lab(1, 2, 9));
    }

    @Test
    void testColorHsl() {
        checkValueObject(new ColorHsl(1, 2, 3), new ColorHsl(1, 2, 3),
                new ColorHsl(9, 2, 3), new ColorHsl(1, 9, 3), new ColorHsl(1, 2, 9));
    }

    @Test
    void testColorHsv() {
        checkValueObject(new ColorHsv(1, 2, 3), new ColorHsv(1, 2, 3),
                new ColorHsv(9, 2, 3), new ColorHsv(1, 9, 3), new ColorHsv(1, 2, 9));
    }

    @Test
    void testColorHunterLab() {
        checkValueObject(new ColorHunterLab(1, 2, 3), new ColorHunterLab(1, 2, 3),
                new ColorHunterLab(9, 2, 3), new ColorHunterLab(1, 9, 3), new ColorHunterLab(1, 2, 9));
    }

    @Test
    void testColorXyz() {
        checkValueObject(new ColorXyz(1, 2, 3), new ColorXyz(1, 2, 3),
                new ColorXyz(9, 2, 3), new ColorXyz(1, 9, 3), new ColorXyz(1, 2, 9));
    }
}
