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
    private /*@ spec_public @*/ UUID id;

    @Column(nullable = false, length = 120)
    private /*@ spec_public @*/ String title;

    @Column(nullable = false, length = 1000)
    private /*@ spec_public @*/ String description;

    @Column(nullable = false)
    private /*@ spec_public @*/ String originalFilename;

    @Column(nullable = false, unique = true)
    private /*@ spec_public @*/ String storageFilename;

    @Column(nullable = false, length = 32)
    private /*@ spec_public @*/ String format;

    @Column(nullable = false, length = 100)
    private /*@ spec_public @*/ String mediaType;

    @Column(nullable = false)
    private /*@ spec_public @*/ int width;

    @Column(nullable = false)
    private /*@ spec_public @*/ int height;

    @Column(nullable = false)
    private /*@ spec_public @*/ long sizeBytes;

    @Column(nullable = false, updatable = false)
    private /*@ spec_public nullable @*/ Instant createdAt;

    @Column(nullable = false)
    private /*@ spec_public nullable @*/ Instant updatedAt;

    /*
     * Class invariants: they mirror the @Column constraints, so an instance that satisfies them can always be persisted.
     */
    //@ public invariant title.length() <= 120;
    //@ public invariant description.length() <= 1000;
    //@ public invariant originalFilename.length() <= 255;
    //@ public invariant format.length() <= 32;
    //@ public invariant mediaType.length() <= 100;
    //@ public invariant width > 0 && height > 0;
    //@ public invariant sizeBytes > 0;
    /** Class invariant: the two timestamps are both set by onCreate() and updatedAt is never set alone. */
    //@ public invariant createdAt != null ==> updatedAt != null;

    protected ImageAsset() {
    }

    /*@ public normal_behavior
      @   requires title.length() <= 120 && description.length() <= 1000 && originalFilename.length() <= 255;
      @   requires format.length() <= 32 && mediaType.length() <= 100;
      @   requires width > 0 && height > 0 && sizeBytes > 0;
      @   ensures this.id == id && this.title == title && this.description == description;
      @   ensures this.originalFilename == originalFilename && this.storageFilename == storageFilename;
      @   ensures this.format == format && this.mediaType == mediaType;
      @   ensures this.width == width && this.height == height && this.sizeBytes == sizeBytes;
      @   ensures this.createdAt == null && this.updatedAt == null; // set only when JPA persists the entity
      @*/
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

    /*@ normal_behavior
      @   assignable createdAt, updatedAt;
      @   ensures createdAt != null && updatedAt == createdAt;
      @*/
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /*@ normal_behavior
      @   requires createdAt != null; // JPA only fires @PreUpdate on an already persisted entity
      @   assignable updatedAt;
      @   ensures updatedAt != null;
      @*/
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /*@ public normal_behavior
      @   ensures \result == id;
      @*/
    public /*@ spec_pure @*/ UUID getId() {
        return id;
    }

    /*@ public normal_behavior
      @   ensures \result == title;
      @*/
    public /*@ spec_pure @*/ String getTitle() {
        return title;
    }

    /*@ public normal_behavior
      @   requires title.length() <= 120;
      @   assignable this.title;
      @   ensures this.title == title;
      @*/
    public void setTitle(String title) {
        this.title = title;
    }

    /*@ public normal_behavior
      @   ensures \result == description;
      @*/
    public /*@ spec_pure @*/ String getDescription() {
        return description;
    }

    /*@ public normal_behavior
      @   requires description.length() <= 1000;
      @   assignable this.description;
      @   ensures this.description == description;
      @*/
    public void setDescription(String description) {
        this.description = description;
    }

    /*@ public normal_behavior
      @   ensures \result == originalFilename;
      @*/
    public /*@ spec_pure @*/ String getOriginalFilename() {
        return originalFilename;
    }

    /*@ public normal_behavior
      @   ensures \result == storageFilename;
      @*/
    public /*@ spec_pure @*/ String getStorageFilename() {
        return storageFilename;
    }

    /*@ public normal_behavior
      @   ensures \result == format;
      @*/
    public /*@ spec_pure @*/ String getFormat() {
        return format;
    }

    /*@ public normal_behavior
      @   ensures \result == mediaType;
      @*/
    public /*@ spec_pure @*/ String getMediaType() {
        return mediaType;
    }

    /*@ public normal_behavior
      @   ensures \result == width;
      @*/
    public /*@ spec_pure @*/ int getWidth() {
        return width;
    }

    /*@ public normal_behavior
      @   ensures \result == height;
      @*/
    public /*@ spec_pure @*/ int getHeight() {
        return height;
    }

    /*@ public normal_behavior
      @   ensures \result == sizeBytes;
      @*/
    public /*@ spec_pure @*/ long getSizeBytes() {
        return sizeBytes;
    }

    /*@ public normal_behavior
      @   ensures \result == createdAt;
      @*/
    public /*@ spec_pure nullable @*/ Instant getCreatedAt() {
        return createdAt;
    }

    /*@ public normal_behavior
      @   ensures \result == updatedAt;
      @*/
    public /*@ spec_pure nullable @*/ Instant getUpdatedAt() {
        return updatedAt;
    }
}
