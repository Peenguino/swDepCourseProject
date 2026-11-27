package it.course.imaging.benchmarks;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import it.course.imaging.image.ImageAsset;
import it.course.imaging.image.ImageAssetRepository;
import it.course.imaging.image.ImageService;

/**
 * B4: {@link ImageService#convertToPng(UUID)} end to end (read from disk, decode, encode PNG).
 * <p>
 * Shows whether the library fixes (H1-H3) reach the application. The repository is a {@link Proxy} that only answers {@code findById}: no Spring context,
 * no database and no Mockito, so nothing but the service code is measured. The stored image is a BMP with photo-like content (gradient plus seeded
 * noise): a flat image would be detected as grayscale and compress to almost nothing.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class ConvertToPngBenchmark {

    @Param({ "256", "1024" })
    private int size;

    private Path storageDir;
    private ImageService service;
    private UUID id;

    @Setup
    public void setUp() throws IOException {
        storageDir = Files.createTempDirectory("jmh-convert-to-png");
        final String storageFilename = "source.bmp";
        final byte[] bmp = Imaging.writeImageToBytes(photoLikeImage(size), ImageFormats.BMP);
        Files.write(storageDir.resolve(storageFilename), bmp);

        id = UUID.randomUUID();
        final ImageAsset asset = new ImageAsset(id, "benchmark", "", storageFilename, storageFilename,
                "BMP", "image/bmp", size, size, bmp.length);
        final ImageAssetRepository repository = (ImageAssetRepository) Proxy.newProxyInstance(
                ImageAssetRepository.class.getClassLoader(), new Class<?>[] { ImageAssetRepository.class },
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.of(asset);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new ImageService(repository, storageDir.toString());
    }

    @TearDown
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(storageDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Benchmark
    public ImageService.PngImage convertToPng() {
        return service.convertToPng(id);
    }

    /** Same content generator as the library benchmarks (BenchmarkImages.Kind.RGB), kept local because test classes are not shared across modules. */
    private static BufferedImage photoLikeImage(int size) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        final Random random = new Random(42L);
        final int[] row = new int[size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int noise = random.nextInt(32);
                final int r = (x * 255 / size + noise) & 0xff;
                final int g = (y * 255 / size + noise) & 0xff;
                final int b = ((x + y) * 127 / size + noise) & 0xff;
                row[x] = 0xff000000 | r << 16 | g << 8 | b;
            }
            image.setRGB(0, y, size, 1, row, 0, size);
        }
        return image;
    }
}
