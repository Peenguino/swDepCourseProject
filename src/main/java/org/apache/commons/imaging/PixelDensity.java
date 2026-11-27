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
package org.apache.commons.imaging;

/**
 * Used to specify pixel density and physical dimensions when reading or storing image information.
 */
public final class PixelDensity {
    private static final /*@ spec_public @*/ int PIXEL_NO_UNIT = 0;
    private static final /*@ spec_public @*/ int PIXEL_PER_INCH = 254;
    private static final /*@ spec_public @*/ int PIXEL_PER_METRE = 10000;
    private static final /*@ spec_public @*/ int PIXEL_PER_CENTIMETRE = 100;

    /**
     * Creates a pixel density from pixels per centimetre.
     *
     * @param x horizontal density in pixels per centimetre.
     * @param y vertical density in pixels per centimetre.
     * @return The pixel density.
     */
    /*@ public normal_behavior
      @   requires x >= 0 && y >= 0; // also rules out NaN
      @   ensures \result.horizontalDensity == x && \result.verticalDensity == y && \result.isInCentimetres();
      @*/
    public static PixelDensity createFromPixelsPerCentimetre(final double x, final double y) {
        return new PixelDensity(x, y, PIXEL_PER_CENTIMETRE);
    }

    /**
     * Creates a pixel density from pixels per inch.
     *
     * @param x horizontal density in pixels per inch.
     * @param y vertical density in pixels per inch.
     * @return The pixel density.
     */
    /*@ public normal_behavior
      @   requires x >= 0 && y >= 0;
      @   ensures \result.horizontalDensity == x && \result.verticalDensity == y && \result.isInInches();
      @*/
    public static PixelDensity createFromPixelsPerInch(final double x, final double y) {
        return new PixelDensity(x, y, PIXEL_PER_INCH);
    }

    /**
     * Creates a pixel density from pixels per metre.
     *
     * @param x horizontal density in pixels per metre.
     * @param y vertical density in pixels per metre.
     * @return The pixel density.
     */
    /*@ public normal_behavior
      @   requires x >= 0 && y >= 0;
      @   ensures \result.horizontalDensity == x && \result.verticalDensity == y && \result.isInMetres();
      @*/
    public static PixelDensity createFromPixelsPerMetre(final double x, final double y) {
        return new PixelDensity(x, y, PIXEL_PER_METRE);
    }

    /**
     * Creates a unitless pixel density.
     *
     * @param x horizontal density (unitless).
     * @param y vertical density (unitless).
     * @return The pixel density.
     */
    /*@ public normal_behavior
      @   requires x >= 0 && y >= 0;
      @   ensures \result.horizontalDensity == x && \result.verticalDensity == y && \result.isUnitless();
      @*/
    public static PixelDensity createUnitless(final double x, final double y) {
        return new PixelDensity(x, y, PIXEL_NO_UNIT);
    }

    private final /*@ spec_public @*/ double horizontalDensity;

    private final /*@ spec_public @*/ double verticalDensity;

    // One-tenth of a millimetre units.
    private final /*@ spec_public @*/ int unitLength;

    /** Class invariant: the unit is one of the four supported ones (no unit, inch, metre, centimetre). */
    //@ public invariant unitLength == 0 || unitLength == 254 || unitLength == 10000 || unitLength == 100;

    /** Class invariant: densities are non-negative numbers (never NaN). */
    //@ public invariant horizontalDensity >= 0 && verticalDensity >= 0;

    /*@ private normal_behavior
      @   requires horizontalDensity >= 0 && verticalDensity >= 0;
      @   requires unitLength == 0 || unitLength == 254 || unitLength == 10000 || unitLength == 100;
      @   ensures this.horizontalDensity == horizontalDensity && this.verticalDensity == verticalDensity;
      @   ensures this.unitLength == unitLength;
      @*/
    private PixelDensity(final double horizontalDensity, final double verticalDensity, final int unitLength) {
        this.horizontalDensity = horizontalDensity;
        this.verticalDensity = verticalDensity;
        this.unitLength = unitLength;
    }

    /**
     * Gets the raw horizontal density.
     *
     * @return The raw horizontal density.
     */
    /*@ public normal_behavior
      @   ensures \result == horizontalDensity;
      @ pure
      @*/
    public double getRawHorizontalDensity() {
        return horizontalDensity;
    }

    /**
     * Gets the raw vertical density.
     *
     * @return The raw vertical density.
     */
    /*@ public normal_behavior
      @   ensures \result == verticalDensity;
      @ pure
      @*/
    public double getRawVerticalDensity() {
        return verticalDensity;
    }

    /**
     * Gets the horizontal density in pixels per centimetre.
     *
     * @return The horizontal density in pixels per centimetre.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInCentimetres() ==> \result == horizontalDensity;
      @   ensures !isInCentimetres() ==> \result == horizontalDensity * PIXEL_PER_CENTIMETRE / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double horizontalDensityCentimetres() {
        if (isInCentimetres()) {
            return horizontalDensity;
        }
        return horizontalDensity * PIXEL_PER_CENTIMETRE / unitLength;
    }

    /**
     * Gets the horizontal density in pixels per inch.
     *
     * @return The horizontal density in pixels per inch.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInInches() ==> \result == horizontalDensity;
      @   ensures !isInInches() ==> \result == horizontalDensity * PIXEL_PER_INCH / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double horizontalDensityInches() {
        if (isInInches()) {
            return horizontalDensity;
        }
        return horizontalDensity * PIXEL_PER_INCH / unitLength;
    }

    /**
     * Gets the horizontal density in pixels per metre.
     *
     * @return The horizontal density in pixels per metre.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInMetres() ==> \result == horizontalDensity;
      @   ensures !isInMetres() ==> \result == horizontalDensity * PIXEL_PER_METRE / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double horizontalDensityMetres() {
        if (isInMetres()) {
            return horizontalDensity;
        }
        return horizontalDensity * PIXEL_PER_METRE / unitLength;
    }

    /**
     * Checks if the density is in centimetres.
     *
     * @return true if in centimetres, false otherwise.
     */
    /*@ public normal_behavior
      @   ensures \result <==> unitLength == 100;
      @ pure
      @*/
    public boolean isInCentimetres() {
        return unitLength == PIXEL_PER_CENTIMETRE;
    }

    /**
     * Checks if the density is in inches.
     *
     * @return true if in inches, false otherwise.
     */
    /*@ public normal_behavior
      @   ensures \result <==> unitLength == 254;
      @ pure
      @*/
    public boolean isInInches() {
        return unitLength == PIXEL_PER_INCH;
    }

    /**
     * Checks if the density is in metres.
     *
     * @return true if in metres, false otherwise.
     */
    /*@ public normal_behavior
      @   ensures \result <==> unitLength == 10000;
      @ pure
      @*/
    public boolean isInMetres() {
        return unitLength == PIXEL_PER_METRE;
    }

    /**
     * Checks if the density is unitless.
     *
     * @return true if unitless, false otherwise.
     */
    /*@ public normal_behavior
      @   ensures \result <==> unitLength == 0;
      @ pure
      @*/
    public boolean isUnitless() {
        return unitLength == PIXEL_NO_UNIT;
    }

    /**
     * Gets the vertical density in pixels per centimetre.
     *
     * @return The vertical density in pixels per centimetre.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInCentimetres() ==> \result == verticalDensity;
      @   ensures !isInCentimetres() ==> \result == verticalDensity * PIXEL_PER_CENTIMETRE / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double verticalDensityCentimetres() {
        if (isInCentimetres()) {
            return verticalDensity;
        }
        return verticalDensity * PIXEL_PER_CENTIMETRE / unitLength;
    }

    /**
     * Gets the vertical density in pixels per inch.
     *
     * @return The vertical density in pixels per inch.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInInches() ==> \result == verticalDensity;
      @   ensures !isInInches() ==> \result == verticalDensity * PIXEL_PER_INCH / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double verticalDensityInches() {
        if (isInInches()) {
            return verticalDensity;
        }
        return verticalDensity * PIXEL_PER_INCH / unitLength;
    }

    /**
     * Gets the vertical density in pixels per metre.
     *
     * @return The vertical density in pixels per metre.
     */
    /*@ public normal_behavior
      @   // converting a unitless density to a physical unit is meaningless (the code would divide by zero)
      @   requires !isUnitless();
      @   ensures isInMetres() ==> \result == verticalDensity;
      @   ensures !isInMetres() ==> \result == verticalDensity * PIXEL_PER_METRE / unitLength;
      @   ensures \result >= 0;
      @ pure
      @*/
    public double verticalDensityMetres() {
        if (isInMetres()) {
            return verticalDensity;
        }
        return verticalDensity * PIXEL_PER_METRE / unitLength;
    }
}
