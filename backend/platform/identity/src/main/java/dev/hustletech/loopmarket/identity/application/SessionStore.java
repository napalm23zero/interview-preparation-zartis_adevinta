package dev.hustletech.loopmarket.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionStore {
    record Session(UUID userId, long accountVersion, Instant expiresAt) {}

    boolean create(String key, Session session, Duration ttl);
    Optional<Session> find(String key);
    void delete(String key);
}
