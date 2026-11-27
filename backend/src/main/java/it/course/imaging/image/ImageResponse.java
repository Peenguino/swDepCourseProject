package it.course.imaging.image;

import java.time.Instant;
import java.util.UUID;

public record ImageResponse(
        UUID id,
        String title,
        String description,
        String originalFilename,
        String format,
        String mediaType,
        int width,
        int height,
        long sizeBytes,
        // JML: null until JPA persists the entity (OpenJML treats unannotated references as non_null)
        /*@ nullable @*/ Instant createdAt,
        /*@ nullable @*/ Instant updatedAt) {

    static ImageResponse from(ImageAsset asset) {
        return new ImageResponse(
                asset.getId(), asset.getTitle(), asset.getDescription(), asset.getOriginalFilename(),
                asset.getFormat(), asset.getMediaType(), asset.getWidth(), asset.getHeight(),
                asset.getSizeBytes(), asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
