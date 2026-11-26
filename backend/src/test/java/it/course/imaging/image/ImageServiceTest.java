package it.course.imaging.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ImageServiceTest {

    @TempDir
    Path tempDir;

    private ImageAssetRepository repository;
    private Path storageDir;
    private ImageService service;

    @BeforeEach
    void setUp() throws IOException {
        repository = mock(ImageAssetRepository.class);
        storageDir = tempDir.resolve("images");
        service = new ImageService(repository, storageDir.toString());
        service.createStorageDirectory();
    }

    @Test
    void testCreateStorageDirectory() throws IOException {
        Path nested = tempDir.resolve("nested/storage/images");
        ImageService nestedService = new ImageService(repository, nested.toString());

        nestedService.createStorageDirectory();

        assertTrue(Files.isDirectory(nested));
    }

    @Test
    void testFindAllMapsEntitiesToResponses() {
        ImageAsset asset = asset("stored.png");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(asset));

        List<ImageResponse> responses = service.findAll();

        assertEquals(1, responses.size());
        ImageResponse response = responses.getFirst();
        assertEquals(asset.getId(), response.id());
        assertEquals(asset.getTitle(), response.title());
        assertEquals(asset.getDescription(), response.description());
        assertEquals(asset.getOriginalFilename(), response.originalFilename());
        assertEquals(asset.getFormat(), response.format());
        assertEquals(asset.getMediaType(), response.mediaType());
        assertEquals(asset.getWidth(), response.width());
        assertEquals(asset.getHeight(), response.height());
        assertEquals(asset.getSizeBytes(), response.sizeBytes());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    void testFindById() {
        ImageAsset asset = asset("stored.png");
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        ImageResponse response = service.findById(asset.getId());

        assertEquals(asset.getId(), response.id());
        assertEquals(asset.getTitle(), response.title());
    }

    @Test
    void testFindByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.findById(id));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Immagine non trovata", exception.getReason());
    }

    @Test
    void testUploadStoresFileAndMetadata() throws IOException {
        byte[] png = png();
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", png);
        when(repository.save(any(ImageAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImageResponse response = service.upload(file, "  Titolo  ", "  Descrizione  ");

        ArgumentCaptor<ImageAsset> captor = ArgumentCaptor.forClass(ImageAsset.class);
        verify(repository).save(captor.capture());
        ImageAsset saved = captor.getValue();
        Path storedFile = storageDir.resolve(saved.getStorageFilename());
        assertTrue(saved.getStorageFilename().endsWith(".png"));
        assertTrue(Files.isRegularFile(storedFile));
        assertArrayEquals(png, Files.readAllBytes(storedFile));
        assertEquals("Titolo", response.title());
        assertEquals("Descrizione", response.description());
        assertEquals("test.png", response.originalFilename());
        assertEquals("PNG", response.format());
        assertEquals("image/png", response.mediaType());
        assertEquals(2, response.width());
        assertEquals(3, response.height());
        assertEquals(png.length, response.sizeBytes());
    }

    @Test
    void testUploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(file, "Titolo", "Descrizione"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Seleziona un'immagine", exception.getReason());
        verifyNoInteractions(repository);
    }

    @Test
    void testUploadRejectsUnknownFormat() {
        MockMultipartFile file = new MockMultipartFile("file", "invalid.bin", "application/octet-stream",
                new byte[] {1, 2, 3, 4});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(file, "Titolo", "Descrizione"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void testUploadTruncatesLongFilenameAndDefaultsMissingName() throws IOException {
        when(repository.save(any(ImageAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String longName = "a".repeat(300) + ".png";

        ImageResponse truncated = service.upload(
                new MockMultipartFile("file", longName, "image/png", png()), "Titolo", "");
        ImageResponse unnamed = service.upload(
                new MockMultipartFile("file", null, "image/png", png()), "Titolo", "");

        assertEquals(255, truncated.originalFilename().length());
        assertTrue(truncated.originalFilename().endsWith(".png"));
        // MockMultipartFile turns a null name into "", so only the null-safe path for an empty name is observable
        assertNotNull(unnamed.originalFilename());
    }

    @Test
    void testUploadRejectsCorruptedImageOfKnownFormat() {
        // Valid PNG signature followed by garbage: the format is recognised but metadata cannot be read
        byte[] corrupted = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "broken.png", "image/png", corrupted);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(file, "Titolo", "Descrizione"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Immagine non valida o non supportata", exception.getReason());
        verifyNoInteractions(repository);
    }

    @Test
    void testUploadRejectsImageAboveMaxPixels() {
        // Header-only PNG declaring 10000x5000 pixels (50M > 40M limit)
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", pngHeader(10_000, 5_000));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(file, "Titolo", "Descrizione"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Immagine troppo grande", exception.getReason());
        verifyNoInteractions(repository);
    }

    @Test
    void testUploadReportsStorageFailure() throws IOException {
        Path blockedPath = tempDir.resolve("not-a-directory");
        Files.writeString(blockedPath, "file");
        ImageService unavailableStorage = new ImageService(repository, blockedPath.toString());
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", png());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> unavailableStorage.upload(file, "Titolo", "Descrizione"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void testUploadDeletesFileWhenRepositoryFails() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", png());
        when(repository.save(any(ImageAsset.class))).thenThrow(new IllegalStateException("Database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.upload(file, "Titolo", "Descrizione"));

        try (var files = Files.list(storageDir)) {
            assertFalse(files.findAny().isPresent(), "Il file orfano deve essere eliminato");
        }
    }

    @Test
    void testUpdateTrimsMetadataAndHandlesNullDescription() {
        ImageAsset asset = asset("stored.png");
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(repository.save(any(ImageAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImageResponse response = service.update(asset.getId(), new UpdateImageRequest("  Nuovo titolo  ", null));

        assertEquals("Nuovo titolo", response.title());
        assertEquals("", response.description());
        assertEquals("Nuovo titolo", asset.getTitle());
        assertEquals("", asset.getDescription());
        verify(repository).save(asset);
    }

    @Test
    void testDeleteRemovesRecordAndFile() throws IOException {
        ImageAsset asset = asset("stored.png");
        Path storedFile = storageDir.resolve(asset.getStorageFilename());
        Files.write(storedFile, png());
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.delete(asset.getId());

        verify(repository).delete(asset);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void testContentReturnsStoredResource() throws IOException {
        byte[] png = png();
        ImageAsset asset = asset("stored.png");
        Files.write(storageDir.resolve(asset.getStorageFilename()), png);
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        ImageService.ImageContent content = service.content(asset.getId());

        assertEquals(asset.getOriginalFilename(), content.filename());
        assertEquals(asset.getMediaType(), content.mediaType());
        assertEquals(asset.getSizeBytes(), content.sizeBytes());
        try (InputStream input = content.resource().getInputStream()) {
            assertArrayEquals(png, input.readAllBytes());
        }
    }

    @Test
    void testContentRejectsMissingFile() {
        ImageAsset asset = asset("missing.png");
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.content(asset.getId()));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertEquals("File non disponibile", exception.getReason());
    }

    @Test
    void testConvertToPngTransformsBmp() throws IOException {
        byte[] bmp = image(ImageFormats.BMP);
        ImageAsset asset = asset("stored.bmp");
        Files.write(storageDir.resolve(asset.getStorageFilename()), bmp);
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        ImageService.PngImage converted = service.convertToPng(asset.getId());

        assertEquals(asset.getTitle() + ".png", converted.filename());
        assertEquals(ImageFormats.PNG, Imaging.guessFormat(converted.bytes()));
        assertEquals(asset.getWidth(), Imaging.getImageSize(converted.bytes()).width);
        assertEquals(asset.getHeight(), Imaging.getImageSize(converted.bytes()).height);
    }

    @Test
    void testConvertToPngRejectsInvalidImage() throws IOException {
        ImageAsset asset = asset("broken.png");
        Files.write(storageDir.resolve(asset.getStorageFilename()), new byte[] {1, 2, 3, 4});
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.convertToPng(asset.getId()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    }

    private ImageAsset asset(String storageFilename) {
        ImageAsset asset = new ImageAsset(UUID.randomUUID(), "Titolo", "Descrizione", "original.png",
                storageFilename, "PNG", "image/png", 2, 3, 4);
        asset.onCreate();
        return asset;
    }

    private static byte[] pngHeader(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13)
                .putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        writeChunk(out, "IHDR", ihdr.array());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(java.io.ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private byte[] png() throws IOException {
        return image(ImageFormats.PNG);
    }

    private byte[] image(ImageFormats format) throws IOException {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        return Imaging.writeImageToBytes(image, format);
    }
}
