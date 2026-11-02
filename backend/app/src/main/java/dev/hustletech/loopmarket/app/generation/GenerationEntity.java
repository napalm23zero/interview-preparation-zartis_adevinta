package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "generation_attempts")
// Records an AI generation attempt and its immutable request identity for reliable retries and audit.
public class GenerationEntity extends GenericEntity {
    @Column(name = "listing_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID listingId;
    @Column(name = "actor_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID actorId;
    @Column(name = "request_key", nullable = false, updatable = false, length = 128) private String requestKey;
    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64) private String fingerprint;
    @Column(name = "input_version", nullable = false, updatable = false) private long inputVersion;
    @Column(name = "client_revision", nullable = false, updatable = false, length = 128) private String clientRevision;
    @Column(nullable = false, updatable = false, length = 32) private String mode;
    @Column(nullable = false, length = 32) private String status = "PENDING";

    protected GenerationEntity() {}
    GenerationEntity(GenerationService.Create input, UUID actor, String fingerprint) {
        this.listingId = input.listingId(); this.actorId = actor; this.requestKey = input.requestKey(); this.fingerprint = fingerprint;
        this.inputVersion = input.inputVersion(); this.clientRevision = input.clientRevision(); this.mode = input.mode();
    }
    UUID listingId() { return listingId; }
    UUID actorId() { return actorId; }
    String requestKey() { return requestKey; }
    String fingerprint() { return fingerprint; }
    long inputVersion() { return inputVersion; }
    String clientRevision() { return clientRevision; }
    String mode() { return mode; }
    String status() { return status; }
    void status(String status) { this.status = status; }
}
