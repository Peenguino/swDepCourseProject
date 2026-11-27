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
import java.util.concurrent.TimeUnit;

import org.apache.commons.imaging.palette.PaletteFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * B2: the two full-image scans that PngWriter runs before encoding (hypothesis H2: per-pixel {@code getRGB(x, y)}).
 * <p>
 * Both are measured on their worst case, where no early exit is possible: an opaque ARGB image for {@code hasTransparency} and an image with gray content for
 * {@code isGrayscale}.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class PaletteFactoryBenchmark {

    @Param({ "256", "1024" })
    private int size;

    private final PaletteFactory factory = new PaletteFactory();
    private BufferedImage opaqueArgb;
    private BufferedImage grayContent;

    @Setup
    public void setUp() {
        opaqueArgb = BenchmarkImages.create(BenchmarkImages.Kind.ARGB_OPAQUE, size);
        grayContent = BenchmarkImages.create(BenchmarkImages.Kind.GRAY_CONTENT, size);
    }

    @Benchmark
    public boolean hasTransparencyFullScan() {
        return factory.hasTransparency(opaqueArgb);
    }

    @Benchmark
    public boolean isGrayscaleFullScan() {
        return factory.isGrayscale(grayContent);
    }
}
