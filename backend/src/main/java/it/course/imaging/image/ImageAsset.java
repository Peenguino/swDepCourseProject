package it.course.imaging.image;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "image_assets")
public class ImageAsset {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String storageFilename;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(nullable = false, length = 100)
    private String mediaType;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ImageAsset() {
    }

    public ImageAsset(UUID id, String title, String description, String originalFilename,
            String storageFilename, String format, String mediaType, int width, int height, long sizeBytes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.originalFilename = originalFilename;
        this.storageFilename = storageFilename;
        this.format = format;
        this.mediaType = mediaType;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageFilename() {
        return storageFilename;
    }

    public String getFormat() {
        return format;
    }

    public String getMediaType() {
        return mediaType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
