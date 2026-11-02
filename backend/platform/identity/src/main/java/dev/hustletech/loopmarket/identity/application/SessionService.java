package dev.hustletech.loopmarket.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Creates, validates, and revokes opaque bearer sessions after provider credentials are verified.
public final class SessionService {
    private final SessionStore store;
    private final Clock clock;
    private final SecureRandom random;

    public SessionService(SessionStore store, Clock clock, SecureRandom random) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    // Call only after the identity provider has verified the user. Trust is earned, even by Sith.
    public String create(UUID verifiedUserId, long accountVersion, Duration ttl) {
        Objects.requireNonNull(verifiedUserId);
        Objects.requireNonNull(ttl);
        if (ttl.isNegative() || ttl.isZero() || ttl.toMillis() < 1) throw new IllegalArgumentException("Session TTL must be positive");
        byte[] bytes = new byte[32];
        // Redis stores only a digest-derived key; a database snapshot cannot reveal the bearer token.
        for (int attempt = 0; attempt < 3; attempt++) {
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (store.create(key(token), new SessionStore.Session(verifiedUserId, accountVersion, clock.instant().plus(ttl)), ttl)) return token;
        }
        throw new IllegalStateException("Unable to create a unique session");
    }

    public Optional<SessionStore.Session> authenticate(String token) {
        // Validate the opaque token's shape before it can become a Redis key.
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return store.find(key(token)).filter(session -> clock.instant().isBefore(session.expiresAt()));
    }

    public void revoke(String token) {
        if (token != null && token.matches("[A-Za-z0-9_-]{43}")) store.delete(key(token));
    }

    private static String key(String token) {
        try {
            return "session:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
