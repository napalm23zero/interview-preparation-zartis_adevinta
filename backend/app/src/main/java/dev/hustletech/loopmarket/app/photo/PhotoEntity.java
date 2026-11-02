package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "listing_photos")
// Tracks one listing image and its validation state; file bytes live in storage, not in MySQL.
public class PhotoEntity extends GenericEntity {
    @Column(name = "listing_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID listingId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "listing_id", insertable = false, updatable = false) private ListingEntity listing;
    @Column(name = "storage_key", nullable = false, length = 512) private String storageKey;
    @Column(name = "media_type", nullable = false, length = 100) private String mediaType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "validation_status", nullable = false, length = 32) private String validationStatus = "PENDING";

    protected PhotoEntity() {}
    PhotoEntity(UUID listingId, String mediaType, long byteSize) {
        this.listingId = listingId; this.storageKey = "photos/" + UUID.randomUUID(); update(mediaType, byteSize);
    }
    void update(String mediaType, long byteSize) { this.mediaType = mediaType; this.byteSize = byteSize; }
    UUID listingId() { return listingId; }
    String mediaType() { return mediaType; }
    long byteSize() { return byteSize; }
    String validationStatus() { return validationStatus; }
    String storageKey() { return storageKey; }
    void uploaded(String key, String mediaType, long byteSize) {
        this.storageKey = key; this.mediaType = mediaType; this.byteSize = byteSize; this.validationStatus = "VALID";
    }
}
