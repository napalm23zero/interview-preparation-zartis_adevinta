package dev.hustletech.loopmarket.identity.adapter.redis;

import dev.hustletech.loopmarket.identity.application.SessionStore;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Redis implementation of the session port; TTL expiry keeps abandoned sessions from haunting the cache.
public final class RedisSessionStore implements SessionStore {
    private final RedisClient redis;

    public RedisSessionStore(RedisClient redis) { this.redis = Objects.requireNonNull(redis); }

    @Override
    public boolean create(String key, Session session, Duration ttl) {
        String value = session.userId() + "|" + session.accountVersion() + "|" + session.expiresAt();
        // NX prevents a rare token collision from overwriting another live session; PX enforces expiry.
        return "OK".equals(redis.set(key, value, SetParams.setParams().nx().px(ttl.toMillis())));
    }

    @Override
    public Optional<Session> find(String key) {
        String value = redis.get(key);
        if (value == null) return Optional.empty();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) return Optional.empty();
        return Optional.of(new Session(UUID.fromString(parts[0]), Long.parseLong(parts[1]), Instant.parse(parts[2])));
    }

    @Override
    public void delete(String key) { redis.del(key); }
}
