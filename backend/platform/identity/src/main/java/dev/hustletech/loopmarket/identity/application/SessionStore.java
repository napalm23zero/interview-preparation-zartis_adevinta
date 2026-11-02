package dev.hustletech.loopmarket.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Storage port for opaque sessions; the application knows expiry and identity, not Redis command lore.
public interface SessionStore {
    record Session(UUID userId, long accountVersion, Instant expiresAt) {}

    boolean create(String key, Session session, Duration ttl);
    Optional<Session> find(String key);
    void delete(String key);
}
