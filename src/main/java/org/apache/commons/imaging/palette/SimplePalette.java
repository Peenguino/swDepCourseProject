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
package org.apache.commons.imaging.palette;

import java.util.Objects;

/**
 * Simple palette implementation.
 */
public class SimplePalette implements Palette {

    private final /*@ spec_public @*/ int[] palette;

    /** Class invariant: the stored palette always exists. */
    //@ public invariant palette != null;

    /**
     * Constructs a new instance.
     *
     * @param palette palette to copy and store.
     */
    /*@ public normal_behavior
      @   requires palette != null;
      @   ensures this.palette.length == palette.length;
      @   ensures (\forall int i; 0 <= i && i < palette.length; this.palette[i] == palette[i]);
      @   ensures this.palette != palette; // defensive copy: no aliasing with the caller's array
      @ also public exceptional_behavior
      @   requires palette == null;
      @   signals_only NullPointerException;
      @*/
    public SimplePalette(final int /*@ nullable @*/ [] palette) {
        // Split in two statements only to work around an OpenJML 21.0.29 type-inference bug on chained generic calls.
        final int[] checked = Objects.requireNonNull(palette, "palette");
        this.palette = checked.clone();
    }

    /*@ also public normal_behavior
      @   requires 0 <= index && index < palette.length;
      @   ensures \result == palette[index];
      @ pure
      @*/
    @Override
    public int getEntry(final int index) {
        return palette[index];
    }

    /*@ also public normal_behavior
      @   ensures -1 <= \result && \result < palette.length;
      @   ensures \result >= 0 ==> palette[\result] == rgb;
      @   ensures \result >= 0 ==> (\forall int k; 0 <= k && k < \result; palette[k] != rgb); // first occurrence
      @   ensures \result == -1 <==> (\forall int k; 0 <= k && k < palette.length; palette[k] != rgb);
      @ pure
      @*/
    @Override
    public int getPaletteIndex(final int rgb) {
        //@ maintaining 0 <= i && i <= palette.length;
        //@ maintaining (\forall int k; 0 <= k && k < i; palette[k] != rgb);
        //@ decreases palette.length - i;
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == rgb) {
                return i;
            }
        }
        return -1;
    }

    /*@ also public normal_behavior
      @   ensures \result == palette.length;
      @ pure
      @*/
    @Override
    public int length() {
        return palette.length;
    }
}
