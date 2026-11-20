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
        Instant createdAt,
        Instant updatedAt) {

    static ImageResponse from(ImageAsset asset) {
        return new ImageResponse(
                asset.getId(), asset.getTitle(), asset.getDescription(), asset.getOriginalFilename(),
                asset.getFormat(), asset.getMediaType(), asset.getWidth(), asset.getHeight(),
                asset.getSizeBytes(), asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
