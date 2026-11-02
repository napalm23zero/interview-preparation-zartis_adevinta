package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "listing_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"actor_id", "operation", "request_key"}))
public class ListingRequestEntity extends GenericEntity {
    @Column(name = "actor_id", nullable = false, columnDefinition = "BINARY(16)") private UUID actorId;
    @Column(nullable = false, length = 16) private String operation;
    @Column(name = "request_key", nullable = false, length = 128) private String requestKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String fingerprint;
    @Column(name = "listing_id", nullable = false, columnDefinition = "BINARY(16)") private UUID listingId;
    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT") private String responseJson;
    protected ListingRequestEntity() {}
    ListingRequestEntity(UUID actorId, String operation, String requestKey, String fingerprint, UUID listingId, String responseJson) {
        this.actorId = actorId; this.operation = operation; this.requestKey = requestKey; this.fingerprint = fingerprint;
        this.listingId = listingId; this.responseJson = responseJson;
    }
    String fingerprint() { return fingerprint; }
    UUID listingId() { return listingId; }
    String responseJson() { return responseJson; }
}
