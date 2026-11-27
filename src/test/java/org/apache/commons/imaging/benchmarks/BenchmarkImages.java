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
package org.apache.commons.imaging.benchmarks;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Deterministic test images shared by the benchmarks.
 * <p>
 * Content is a gradient plus seeded noise, so it compresses like a photo rather than like a flat image (a flat image would make the Deflater cost vanish
 * and hide the per-pixel work we want to measure).
 * </p>
 */
public final class BenchmarkImages {

    /** Kinds of image used to drive the different code paths of the PNG writer. */
    public enum Kind {
        /** TYPE_INT_RGB with colored content: true-color path, isGrayscale exits at the first pixel. */
        RGB,
        /** TYPE_INT_ARGB, every pixel opaque: hasTransparency must scan the whole image, then true-color path. */
        ARGB_OPAQUE,
        /** TYPE_INT_RGB with r == g == b: isGrayscale must scan the whole image, then grayscale path. */
        GRAY_CONTENT
    }

    private static final long SEED = 42L;

    /**
     * Creates a deterministic image.
     *
     * @param kind the kind of image.
     * @param size width and height in pixels.
     * @return a new image.
     */
    public static BufferedImage create(final Kind kind, final int size) {
        final int type = kind == Kind.ARGB_OPAQUE ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        final BufferedImage image = new BufferedImage(size, size, type);
        final Random random = new Random(SEED);
        final int[] row = new int[size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int noise = random.nextInt(32);
                final int r = (x * 255 / size + noise) & 0xff;
                final int g = (y * 255 / size + noise) & 0xff;
                final int b = ((x + y) * 127 / size + noise) & 0xff;
                if (kind == Kind.GRAY_CONTENT) {
                    final int gray = (r + g + b) / 3;
                    row[x] = 0xff000000 | gray << 16 | gray << 8 | gray;
                } else {
                    row[x] = 0xff000000 | r << 16 | g << 8 | b;
                }
            }
            image.setRGB(0, y, size, 1, row, 0, size);
        }
        return image;
    }

    private BenchmarkImages() {
    }
}
