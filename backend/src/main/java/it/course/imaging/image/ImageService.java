package it.course.imaging.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageService.class);
    private static final long MAX_PIXELS = 40_000_000L;

    private final ImageAssetRepository repository;
    private final Path storageDir;

    public ImageService(ImageAssetRepository repository, @Value("${app.storage-dir}") String storageDir) {
        this.repository = repository;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(storageDir);
    }

    public List<ImageResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(ImageResponse::from).toList();
    }

    public ImageResponse findById(UUID id) {
        return ImageResponse.from(getAsset(id));
    }

    public ImageResponse upload(MultipartFile file, String title, String description) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleziona un'immagine");
        }

        byte[] bytes;
        ImageFormat format;
        ImageInfo info;
        try {
            bytes = file.getBytes();
            format = Imaging.guessFormat(bytes);
            if (format == ImageFormats.UNKNOWN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato non supportato");
            }
            info = Imaging.getImageInfo(bytes);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Immagine non valida o non supportata", e);
        }

        if ((long) info.getWidth() * info.getHeight() > MAX_PIXELS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Immagine troppo grande");
        }

        String storageFilename = UUID.randomUUID() + "." + format.getDefaultExtension();
        Path destination = storageDir.resolve(storageFilename);
        try {
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile salvare l'immagine", e);
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
        if (originalFilename.length() > 255) {
            originalFilename = originalFilename.substring(originalFilename.length() - 255);
        }
        String mediaType = info.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : info.getMimeType();
        ImageAsset asset = new ImageAsset(
                UUID.randomUUID(), title.trim(), description.trim(), originalFilename, storageFilename,
                format.getName(), mediaType, info.getWidth(), info.getHeight(), bytes.length);

        try {
            return ImageResponse.from(repository.save(asset));
        } catch (RuntimeException e) {
            deleteQuietly(destination);
            throw e;
        }
    }

    public ImageResponse update(UUID id, UpdateImageRequest request) {
        ImageAsset asset = getAsset(id);
        asset.setTitle(request.title().trim());
        asset.setDescription(request.description() == null ? "" : request.description().trim());
        return ImageResponse.from(repository.save(asset));
    }

    public void delete(UUID id) {
        ImageAsset asset = getAsset(id);
        repository.delete(asset);
        deleteQuietly(storageDir.resolve(asset.getStorageFilename()));
    }

    public ImageContent content(UUID id) {
        ImageAsset asset = getAsset(id);
        Resource resource = new FileSystemResource(storageDir.resolve(asset.getStorageFilename()));
        if (!resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File non disponibile");
        }
        return new ImageContent(resource, asset.getOriginalFilename(), asset.getMediaType(), asset.getSizeBytes());
    }

    public PngImage convertToPng(UUID id) {
        ImageAsset asset = getAsset(id);
        try {
            byte[] input = Files.readAllBytes(storageDir.resolve(asset.getStorageFilename()));
            BufferedImage image = Imaging.getBufferedImage(input);
            byte[] output = Imaging.writeImageToBytes(image, ImageFormats.PNG);
            return new PngImage(output, asset.getTitle() + ".png");
        } catch (ImagingException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Questo formato non può essere decodificato", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversione non riuscita", e);
        }
    }

    private ImageAsset getAsset(UUID id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Immagine non trovata"));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("Impossibile eliminare il file {}", path, e);
        }
    }

    public record ImageContent(Resource resource, String filename, String mediaType, long sizeBytes) {
    }

    public record PngImage(byte[] bytes, String filename) {
    }
}
