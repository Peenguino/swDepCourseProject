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
    private static final /*@ spec_public @*/ long MAX_PIXELS = 40_000_000L;

    private final /*@ spec_public @*/ ImageAssetRepository repository;
    private final /*@ spec_public @*/ Path storageDir;

    /*
     * Model methods used by the specifications below. String.trim() cannot appear in a JML clause (OpenJML rejects it
     * as non-pure), so "blank" and "trimmed" are defined with charAt(), mirroring trim()'s definition (chars <= ' ').
     */
    /*@ public static model pure boolean isNotBlank(String s) {
      @   return (\exists int i; 0 <= i && i < s.length(); s.charAt(i) > ' ');
      @ }
      @ public static model pure boolean isTrimmed(String s) {
      @   return s.length() == 0 || (s.charAt(0) > ' ' && s.charAt(s.length() - 1) > ' ');
      @ }
      @*/

    /** Class invariant: the collaborators are always set (Spring injects them through the constructor). */
    //@ public invariant repository != null && storageDir != null;

    /*@ public behavior
      @   requires repository != null && storageDir != null;
      @   ensures this.repository == repository;
      @   signals_only RuntimeException; // Path.of rejects malformed paths (InvalidPathException)
      @*/
    public ImageService(ImageAssetRepository repository, @Value("${app.storage-dir}") String storageDir) {
        this.repository = repository;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(storageDir);
    }

    /*@ public behavior
      @   ensures \result != null;
      @   signals_only RuntimeException; // repository failures are not handled here
      @*/
    public List<ImageResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(ImageResponse::from).toList();
    }

    /*@ public behavior
      @   requires id != null;
      @   ensures \result != null;
      @   signals_only RuntimeException; // ResponseStatusException 404 when the id is unknown
      @*/
    //+RAC@ ensures \result.id().equals(id); // UUID.equals has no JML spec: checked at runtime (RAC) only
    public ImageResponse findById(UUID id) {
        return ImageResponse.from(getAsset(id));
    }

    /*@ public behavior
      @   // same constraints the controller enforces with @NotBlank / @Size
      @   requires file != null;
      @   requires isNotBlank(title) && title.length() <= 120;
      @   requires description.length() <= 1000;
      @   ensures \result != null;
      @   ensures isNotBlank(\result.title()) && isTrimmed(\result.title()) && \result.title().length() <= title.length();
      @   ensures isTrimmed(\result.description()) && \result.description().length() <= description.length();
      @   ensures \result.originalFilename().length() <= 255; // long client filenames are truncated
      @   ensures \result.sizeBytes() > 0;
      @   ensures \result.width() > 0 && \result.height() > 0;
      @   ensures (long) \result.width() * \result.height() <= MAX_PIXELS;
      @   signals_only RuntimeException; // ResponseStatusException 400/500 for empty, invalid or too large images
      @*/
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

    /*@ public behavior
      @   requires id != null && request != null;
      @   requires isNotBlank(request.title()) && request.title().length() <= 120;
      @   requires request.description() == null || request.description().length() <= 1000;
      @   ensures \result != null;
      @   ensures isNotBlank(\result.title()) && isTrimmed(\result.title());
      @   ensures \result.title().length() <= request.title().length();
      @   ensures request.description() == null ==> \result.description().length() == 0;
      @   ensures request.description() != null ==> isTrimmed(\result.description())
      @                                          && \result.description().length() <= request.description().length();
      @   signals_only RuntimeException; // ResponseStatusException 404 when the id is unknown
      @*/
    //+RAC@ ensures \result.id().equals(id); // UUID.equals has no JML spec: checked at runtime (RAC) only
    public ImageResponse update(UUID id, UpdateImageRequest request) {
        ImageAsset asset = getAsset(id);
        asset.setTitle(request.title().trim());
        asset.setDescription(request.description() == null ? "" : request.description().trim());
        return ImageResponse.from(repository.save(asset));
    }

    /*@ public behavior
      @   requires id != null;
      @   signals_only RuntimeException; // ResponseStatusException 404 when the id is unknown
      @*/
    public void delete(UUID id) {
        ImageAsset asset = getAsset(id);
        repository.delete(asset);
        deleteQuietly(storageDir.resolve(asset.getStorageFilename()));
    }

    /*@ public behavior
      @   requires id != null;
      @   ensures \result != null && \result.sizeBytes() > 0;
      @   signals_only RuntimeException; // 404 unknown id, 500 file missing on disk
      @*/
    public ImageContent content(UUID id) {
        ImageAsset asset = getAsset(id);
        Resource resource = new FileSystemResource(storageDir.resolve(asset.getStorageFilename()));
        if (!resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File non disponibile");
        }
        return new ImageContent(resource, asset.getOriginalFilename(), asset.getMediaType(), asset.getSizeBytes());
    }

    /*@ public behavior
      @   requires id != null;
      @   ensures \result != null && \result.bytes().length > 0;
      @   ensures \result.filename().endsWith(".png");
      @   signals_only RuntimeException; // 404 unknown id, 422 undecodable format, 500 I/O error
      @*/
    public PngImage convertToPng(UUID id) {
        ImageAsset asset = getAsset(id);
        try {
            byte[] input = Files.readAllBytes(storageDir.resolve(asset.getStorageFilename()));
            BufferedImage image = Imaging.getBufferedImage(input);
            byte[] output = Imaging.writeImageToBytes(image, ImageFormats.PNG);
            return new PngImage(output, asset.getTitle() + ".png");
        } catch (ImagingException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Questo formato non può essere decodificato", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversione non riuscita", e);
        }
    }

    /*@ private behavior
      @   requires id != null;
      @   ensures \result != null;
      @   signals_only RuntimeException; // ResponseStatusException 404 when the id is unknown
      @*/
    //+RAC@ ensures \result.getId().equals(id); // UUID.equals has no JML spec: checked at runtime (RAC) only
    private ImageAsset getAsset(UUID id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Immagine non trovata"));
    }

    /*@ private normal_behavior
      @   // "quietly": an I/O failure is logged, never propagated
      @   requires path != null;
      @*/
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
