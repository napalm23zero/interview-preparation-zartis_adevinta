package dev.hustletech.loopmarket.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Links a local person to an external issuer subject; provider credentials stay outside this model.
public record User(UUID id, UUID personId, String issuer, String externalSubject,
                   Instant createdAt, Instant updatedAt, Instant deletedAt) {
    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(externalSubject, "externalSubject");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (id.version() != 7 || id.variant() != 2 || personId.version() != 7 || personId.variant() != 2) {
            throw new IllegalArgumentException("User and person ids must be UUIDv7");
        }
        if (issuer.isBlank() || externalSubject.isBlank()) {
            throw new IllegalArgumentException("Issuer and external subject must not be blank");
        }
    }
}
