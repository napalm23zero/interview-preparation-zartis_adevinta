package dev.hustletech.loopmarket.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Person(UUID id, String displayName, Instant createdAt, Instant updatedAt, Instant deletedAt) {
    public Person {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException("Person id must be UUIDv7");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name must not be blank");
        }
    }
}
