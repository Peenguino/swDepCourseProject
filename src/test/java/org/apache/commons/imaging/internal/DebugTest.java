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
package org.apache.commons.imaging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Captures the FINEST log records emitted by {@link Debug} for each supported value type.
 */
class DebugTest {

    private static final Logger LOGGER = Logger.getLogger(Debug.class.getName());

    private final List<String> messages = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public void flush() {
            // nothing buffered
        }

        @Override
        public void publish(final LogRecord record) {
            messages.add(record.getMessage());
        }
    };
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = LOGGER.getLevel();
        LOGGER.setLevel(Level.FINEST);
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
    }

    @AfterEach
    void restoreLogger() {
        LOGGER.removeHandler(handler);
        LOGGER.setLevel(previousLevel);
    }

    private String last() {
        return messages.get(messages.size() - 1);
    }

    @Test
    void testDisabledLoggerEmitsNothing() {
        LOGGER.setLevel(Level.INFO);
        Debug.debug("hidden");
        Debug.debug();
        assertTrue(messages.isEmpty());
    }

    @Test
    void testScalarValues() {
        Debug.debug("plain");
        assertEquals("plain", last());

        Debug.debug();
        assertEquals("\r\n", last());

        Debug.debug("nothing", (Object) null);
        assertEquals("nothing null", last());

        Debug.debug("text", (Object) "value");
        assertEquals("text value", last());

        Debug.debug("number", Integer.valueOf(42));
        assertEquals("number 42", last());

        Debug.debug("file", new File("a.txt"));
        assertEquals("file: a.txt", last());
    }

    @Test
    void testDatesUseFixedFormat() {
        final Calendar calendar = new GregorianCalendar(2020, Calendar.MARCH, 4, 5, 6, 7);
        calendar.setTimeZone(TimeZone.getDefault());
        Debug.debug("cal", calendar);
        assertEquals("cal 03/04/2020 05:06:07", last());

        final Date date = calendar.getTime();
        Debug.debug("date", date);
        assertEquals("date 03/04/2020 05:06:07", last());
    }

    @Test
    void testArrays() {
        Debug.debug("chars", new char[] { 'a', 'b' });
        assertTrue(last().startsWith("chars (2)"));
        assertTrue(last().contains("\ta (97)"));

        Debug.debug("ints", new int[] { 7, 8 });
        assertTrue(last().startsWith("ints (2)"));
        assertTrue(last().contains("\t8"));

        Debug.debug("bytes", new byte[] { 0, 'A', 10, 11, 13 });
        final String bytes = last();
        assertTrue(bytes.startsWith("bytes (5)"));
        assertTrue(bytes.contains("1: 65 (A, 0x41)"));
        assertTrue(bytes.contains("0: 0 ( , 0x0)"), "control characters are blanked");
        assertTrue(bytes.contains("4: 13 ( , 0xd)"));

        final byte[] large = new byte[300];
        Debug.debug("large", large);
        assertTrue(last().contains("249: 0"));
        assertTrue(last().contains("\t..."), "output is truncated after 250 bytes");
    }

    @Test
    void testCollections() {
        Debug.debug("list", Arrays.asList("x", "y"));
        // header, one line per element, then an empty debug() line
        final List<String> tail = messages.subList(messages.size() - 4, messages.size());
        assertTrue(tail.get(0).startsWith("list (2) ["));
        assertTrue(tail.get(1).startsWith("\tx ["));
        assertTrue(tail.get(2).startsWith("\ty ["));
        assertEquals("\r\n", tail.get(3));

        final Map<String, Integer> map = new LinkedHashMap<>();
        map.put("k", 1);
        Debug.debug("map", map);
        assertTrue(last().startsWith("map map: 1"));
        assertTrue(last().contains("0: 'k' -> '1'"));

        Debug.debug("empty", Collections.emptyMap());
        assertTrue(last().startsWith("empty map: 0"));
    }

    @Test
    void testIccProfile() {
        final ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        Debug.debug("srgb", profile);
        final List<String> tail = messages.subList(messages.size() - 4, messages.size());
        assertTrue(tail.get(0).startsWith("ICC_Profile srgb: "));
        assertTrue(tail.get(1).startsWith("\t getProfileClass: "));
        assertTrue(tail.get(2).startsWith("\t getPCSType: "));
        assertTrue(tail.get(3).contains("byteQuad: " + ColorSpace.TYPE_RGB), "Java color space type constant");
    }

    @Test
    void testThrowables() {
        final Exception cause = new IllegalStateException("broken");
        Debug.debug(cause);
        assertTrue(last().contains("Throwable: (java.lang.IllegalStateException)"));
        assertTrue(last().contains("Throwable: broken"));
        assertTrue(last().contains("Caught here:"));

        Debug.debug(cause, 1);
        assertTrue(last().contains("\t..."), "stack trace limited to one frame");

        Debug.debug((Throwable) null);
        assertTrue(last().contains("Throwable: null"));
    }
}
