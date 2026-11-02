package dev.hustletech.loopmarket.identity.application;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final MemoryStore store = new MemoryStore();
    private final SessionService sessions = new SessionService(store, clock, new SecureRandom());

    @Test void authenticatesAndRevokesWithoutStoringTheBearerToken() {
        UUID user = UUID.randomUUID();
        String token = sessions.create(user, 7, Duration.ofMinutes(5));
        assertEquals(Optional.of(user), sessions.authenticate(token).map(SessionStore.Session::userId));
        assertEquals(7, sessions.authenticate(token).orElseThrow().accountVersion());
        assertFalse(store.entries.containsKey("session:" + token));
        sessions.revoke(token);
        assertTrue(sessions.authenticate(token).isEmpty());
    }

    @Test void rejectsExpiredAndMalformedSessions() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(5));
        SessionService later = new SessionService(store, Clock.offset(clock, Duration.ofMinutes(5)), new SecureRandom());
        assertTrue(later.authenticate(token).isEmpty());
        assertTrue(sessions.authenticate("invalid").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> sessions.create(UUID.randomUUID(), 0, Duration.ZERO));
    }

    private static final class MemoryStore implements SessionStore {
        private final Map<String, Session> entries = new HashMap<>();
        public boolean create(String key, Session session, Duration ttl) { return entries.putIfAbsent(key, session) == null; }
        public Optional<Session> find(String key) { return Optional.ofNullable(entries.get(key)); }
        public void delete(String key) { entries.remove(key); }
    }
}
