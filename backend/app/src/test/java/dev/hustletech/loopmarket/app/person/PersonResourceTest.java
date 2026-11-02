package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.security.Principal;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class PersonResourceTest {
    private final MemoryRepository repository = new MemoryRepository();
    final ResourceExtension resources = ResourceExtension.builder()
            .setTestContainerFactory(new org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory())
            .addProvider((ContainerRequestFilter) PersonResourceTest::authenticateAdmin)
            .addResource(new PersonResource(new PersonService(repository, Clock.systemUTC(), unusedSessionFactory())))
            .build();

    @Test void inheritedPostDeserializesAndValidatesConcreteInput() {
        try (Response response = resources.target("/persons").request().post(Entity.json("{\"displayName\":\"Leia Organa\"}"))) {
            assertEquals(201, response.getStatus());
            assertTrue(response.readEntity(String.class).contains("Leia Organa"));
        }
        try (Response response = resources.target("/persons").request().post(Entity.json("{\"displayName\":\"  \"}"))) {
            assertTrue(response.getStatus() >= 400 && response.getStatus() < 500);
        }
    }

    private static void authenticateAdmin(ContainerRequestContext request) {
        request.setSecurityContext(new SecurityContext() {
            public Principal getUserPrincipal() { return () -> "admin"; }
            public boolean isUserInRole(String role) { return "ADMIN".equals(role); }
            public boolean isSecure() { return true; }
            public String getAuthenticationScheme() { return "Bearer"; }
        });
    }

    private static org.hibernate.SessionFactory unusedSessionFactory() {
        return (org.hibernate.SessionFactory) java.lang.reflect.Proxy.newProxyInstance(
                org.hibernate.SessionFactory.class.getClassLoader(), new Class<?>[]{org.hibernate.SessionFactory.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException("No database is used by this test"); });
    }

    private static final class MemoryRepository implements GenericRepository<PersonEntity> {
        private final Map<UUID, PersonEntity> entries = new HashMap<>();
        public PersonEntity create(PersonEntity entity) {
            try {
                var callback = GenericEntity.class.getDeclaredMethod("onCreate");
                callback.setAccessible(true);
                callback.invoke(entity);
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            entries.put(entity.getId(), entity);
            return entity;
        }
        public Optional<PersonEntity> findActive(UUID id) { return Optional.ofNullable(entries.get(id)).filter(e -> !e.isDeleted()); }
        public List<PersonEntity> findAllActive(int offset, int limit) {
            return entries.values().stream().filter(e -> !e.isDeleted()).skip(offset).limit(limit).toList();
        }
        public long countActive() { return entries.values().stream().filter(e -> !e.isDeleted()).count(); }
    }
}
