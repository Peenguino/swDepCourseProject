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
package org.apache.commons.imaging.formats.png;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

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
 * B3: chunk CRC as computed by PngWriter.writeChunk (hypothesis H3), compared with the JDK {@link CRC32} on the same bytes.
 * <p>
 * Lives in the {@code formats.png} package because {@link PngCrc} is package-private. {@code pngCrcPerChunk} mirrors writeChunk exactly: a new PngCrc per
 * chunk (so the 256-entry table is rebuilt every time), CRC over the chunk type, then over the data.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class PngCrcBenchmark {

    /** Chunk data length in bytes: a small chunk, the 64 KiB typical of IDAT, and a 1 MiB IDAT. */
    @Param({ "1024", "65536", "1048576" })
    private int length;

    private final byte[] type = ChunkType.IDAT.array;
    private byte[] data;

    @Setup
    public void setUp() {
        data = new byte[length];
        new Random(42L).nextBytes(data);
    }

    @Benchmark
    public int jdkCrc32() {
        final CRC32 crc32 = new CRC32();
        crc32.update(type, 0, type.length);
        crc32.update(data, 0, data.length);
        return (int) crc32.getValue();
    }

    @Benchmark
    public int pngCrcPerChunk() {
        final PngCrc pngCrc = new PngCrc();
        final long crc1 = pngCrc.startPartialCrc(type, type.length);
        final long crc2 = pngCrc.continuePartialCrc(crc1, data, data.length);
        return (int) pngCrc.finishPartialCrc(crc2);
    }
}
