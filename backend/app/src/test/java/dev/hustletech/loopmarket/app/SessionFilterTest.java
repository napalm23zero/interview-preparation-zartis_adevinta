package dev.hustletech.loopmarket.app;

import dev.hustletech.loopmarket.identity.application.SessionService;
import dev.hustletech.loopmarket.identity.application.SessionStore;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
// Verifies each request rechecks Redis and MySQL, including fail-closed dependency errors.
class SessionFilterTest {
    private final MemoryStore store = new MemoryStore();
    private final java.util.concurrent.atomic.AtomicLong accountVersion = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean accountActive = new java.util.concurrent.atomic.AtomicBoolean(true);
    private final SessionService sessions = new SessionService(store, Clock.systemUTC(), new SecureRandom());
    final ResourceExtension resources = ResourceExtension.builder()
            .setTestContainerFactory(new org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory())
            .addProvider(new SessionFilter(sessions, activeUserDatabase(accountVersion, accountActive)))
            .addProvider(new ProblemResponseFilter())
            .addProvider(new WebProblemMapper())
            .addResource(new SecuredResource()).addResource(new BusinessResource())
            .addResource(new SessionResource(sessions, null, "", new com.fasterxml.jackson.databind.ObjectMapper())).build();

    @Test void checksStoreOnEveryRequestAndRejectsRevokedSession() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(1));
        assertEquals(200, get(token));
        assertEquals(200, get(token));
        assertEquals(2, store.reads);
        sessions.revoke(token);
        assertEquals(401, get(token));
        assertEquals(3, store.reads);
        assertEquals(401, get("malformed"));
    }

    @Test void doesNotAuthenticateWhenSessionStoreFails() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(1));
        store.fail = true;
        try (Response response = resources.target("/secured").request().header("Authorization", "Bearer " + token).get()) {
            assertEquals(503, response.getStatus());
            assertEquals("application/problem+json", response.getMediaType().toString());
            assertFalse(response.readEntity(String.class).contains("Store unavailable"));
        }
    }

    @Test void logoutFailureDoesNotClaimTheSessionWasRevoked() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(1));
        store.failDelete = true;
        try (Response response = resources.target("/sessions").request().header("Authorization", "Bearer " + token).delete()) {
            assertEquals(503, response.getStatus());
            assertEquals("application/problem+json", response.getMediaType().toString());
            assertTrue(response.readEntity(String.class).contains("Session revocation could not be confirmed"));
        }
        assertEquals(200, get(token));
        store.failDelete = false;
        try (Response response = resources.target("/sessions").request().header("Authorization", "Bearer " + token).delete()) {
            assertEquals(204, response.getStatus());
        }
        assertEquals(401, get(token));
    }

    @Test void rejectedCredentialsKeepChallengeAndProblemBody() {
        try (Response response = resources.target("/secured").request().header("Authorization", "Bearer malformed").get()) {
            assertEquals(401, response.getStatus());
            assertEquals("Bearer", response.getHeaderString("WWW-Authenticate"));
            assertEquals("application/problem+json", response.getMediaType().toString());
        }
    }

    @Test void rejectsSessionAfterAccountChanges() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(1));
        assertEquals(200, get(token));
        accountVersion.incrementAndGet();
        assertEquals(401, get(token));
    }

    @Test void rejectsSessionAfterAccountIsDeleted() {
        String token = sessions.create(UUID.randomUUID(), 0, Duration.ofMinutes(1));
        assertEquals(200, get(token));
        accountActive.set(false);
        assertEquals(401, get(token));
    }

    private int get(String token) {
        try (Response response = resources.target("/secured").request().header("Authorization", "Bearer " + token).get()) {
            return response.getStatus();
        }
    }

    @Path("/secured") public static final class SecuredResource {
        @GET public Response get(@Context SecurityContext security) {
            return security.getUserPrincipal() == null ? Response.status(401).build() : Response.ok().build();
        }
    }

    @Test void businessErrorsKeepTheirSafeDetails() {
        try (Response response = resources.target("/business").request().get()) {
            assertEquals(409, response.getStatus());
            assertEquals("application/problem+json", response.getMediaType().toString());
            assertTrue(response.readEntity(String.class).contains("Listing version does not match"));
        }
    }

    @Path("/business") public static final class BusinessResource {
        @GET public Response get() { throw new jakarta.ws.rs.ClientErrorException("Listing version does not match", 409); }
    }

    private static DataSource activeUserDatabase(java.util.concurrent.atomic.AtomicLong accountVersion,
                                                  java.util.concurrent.atomic.AtomicBoolean accountActive) {
        java.util.concurrent.atomic.AtomicLong requestedVersion = new java.util.concurrent.atomic.AtomicLong();
        ResultSet result = proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> accountActive.get() && requestedVersion.get() == accountVersion.get();
            case "getString" -> "USER";
            default -> null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
            if (method.equals("setLong")) requestedVersion.set((long) args[1]);
            return method.equals("executeQuery") ? result : null;
        });
        Connection connection = proxy(Connection.class, (method, args) -> method.equals("prepareStatement") ? statement : null);
        return proxy(DataSource.class, (method, args) -> method.equals("getConnection") ? connection : null);
    }

    private interface Handler { Object call(String method, Object[] arguments); }
    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> handler.call(method.getName(), args)));
    }
    private static final class MemoryStore implements SessionStore {
        private final Map<String, Session> entries = new HashMap<>();
        private int reads;
        private boolean fail;
        private boolean failDelete;
        public boolean create(String key, Session session, Duration ttl) { return entries.putIfAbsent(key, session) == null; }
        public Optional<Session> find(String key) {
            reads++;
            if (fail) throw new IllegalStateException("Store unavailable");
            return Optional.ofNullable(entries.get(key));
        }
        public void delete(String key) {
            if (failDelete) throw new IllegalStateException("Store unavailable");
            entries.remove(key);
        }
    }
}
