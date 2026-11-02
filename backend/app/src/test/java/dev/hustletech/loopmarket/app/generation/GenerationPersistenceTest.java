package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
// Proves generation request keys are retry-safe and cannot be replayed with a different payload.
class GenerationPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(GenerationEntity.class).build();

    @Test void requestKeyIsIdempotentAndCannotBeReusedForDifferentInput() {
        UUID actor = UUID.randomUUID();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), actor, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        GenerationService generations = service(actor, false);
        GenerationService.Create input = new GenerationService.Create(listing, "key-1", 0L, "revision-1", "LOCAL");
        UUID id = database.inTransaction(() -> generations.create(input).getId());
        assertEquals(id, database.inTransaction(() -> generations.create(input).getId()));
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> generations.create(
                new GenerationService.Create(listing, "key-1", 0L, "revision-2", "LOCAL"))));
        assertThrows(ForbiddenException.class, () -> database.inTransaction(() -> generations.update(id, new GenerationService.Update("FAILED"))));
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> generations.create(
                new GenerationService.Create(listing, "key-2", 0L, "revision-1", "UNSUPPORTED"))));
        assertEquals(0L, database.inTransaction(() -> service(UUID.randomUUID(), false).count()).longValue());
    }

    @Test void deterministicSuggestionsPersistAndNeverModifyTheDraft() {
        UUID actor = UUID.randomUUID();
        var listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), actor, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Holocron", "Ancient book", "BOOKS", null, null, null, null, null)).getId());
        var generations = service(actor, false);
        var request = new GenerationService.Create(listing, "demo-1", 0L, "revision-1", "DETERMINISTIC");
        UUID id = database.inTransaction(() -> generations.create(request).getId());
        database.inTransaction(() -> {
            var result = generations.find(id).fold(value -> value, () -> null);
            assertEquals("COMPLETED", result.status());
            assertEquals("deterministic", result.provider());
            assertEquals("Holocron", result.suggestions().get("title"));
            assertEquals("Ancient book", result.suggestions().get("description"));
            assertEquals("BOOKS", result.suggestions().get("category"));
            assertEquals(0L, listings.find(listing).fold(ListingEntity::getVersion, () -> -1L));
            assertEquals(id, generations.create(request).getId());
            return null;
        });
        UUID empty = database.inTransaction(() -> listings.create(new ListingService.Input(null, null, null, null, null, null, null, null)).getId());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> generations.create(
                new GenerationService.Create(empty, "empty", 0L, "revision-1", "DETERMINISTIC"))));
        UUID malicious = database.inTransaction(() -> listings.create(new ListingService.Input("<script>", null, null, null, null, null, null, null)).getId());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> generations.create(
                new GenerationService.Create(malicious, "demo-2", 0L, "revision-1", "DETERMINISTIC"))));
    }

    @Test void localGenerationClaimsOnceAndKeepsUncertainFailuresFromRetrying() throws Exception {
        UUID actor = UUID.randomUUID();
        var listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), actor, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Book", "Used book", "BOOKS", null, null, null, null, null)).getId());
        var generations = service(actor, false);
        UUID id = database.inTransaction(() -> generations.create(new GenerationService.Create(listing, "local-1", 0L, "r1", "LOCAL")).getId());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var runnerRef = new java.util.concurrent.atomic.AtomicReference<GenerationRunner>();
        var runner = new GenerationRunner(database.getSessionFactory(), facts -> {
            calls.incrementAndGet();
            assertEquals("Book", facts.get("title"));
            assertEquals("BOOKS", facts.get("category"));
            assertEquals("PROCESSING", readStatus(id));
            try { assertEquals("PROCESSING", runnerRef.get().run(id, actor).status()); }
            catch (Exception exception) { throw new AssertionError(exception); }
            assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> service(actor, true).update(id, new GenerationService.Update("FAILED"))));
            assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> service(actor, true).delete(id)));
            return new GatewayClient.Result("ollama", "local-model", java.util.Map.of("title", "Book"));
        }, Clock.systemUTC());
        runnerRef.set(runner);
        assertEquals("COMPLETED", runner.run(id, actor).status());
        assertEquals("COMPLETED", runner.run(id, actor).status());
        assertEquals(1, calls.get());
        UUID failed = database.inTransaction(() -> generations.create(new GenerationService.Create(listing, "local-2", 0L, "r1", "LOCAL")).getId());
        var failing = new GenerationRunner(database.getSessionFactory(), facts -> { throw new java.io.IOException("timeout"); }, Clock.systemUTC());
        assertThrows(java.io.IOException.class, () -> failing.run(failed, actor));
        assertEquals("UNKNOWN", readStatus(failed));
        assertEquals("UNKNOWN", runner.run(failed, actor).status());
        assertEquals(1, calls.get());
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> runner.run(failed, UUID.randomUUID()));
    }

    @Test void pollingMarksOnlyStaleProcessingAttemptsUnknown() {
        UUID actor = UUID.randomUUID();
        var now = java.time.Instant.parse("2026-11-02T12:00:00Z");
        var listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), actor, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Book", null, null, null, null, null, null, null)).getId());
        UUID id = database.inTransaction(() -> service(actor, false).create(new GenerationService.Create(listing, "stale", 0L, "r1", "LOCAL")).getId());
        database.inTransaction(() -> {
            var attempt = database.getSessionFactory().getCurrentSession().find(GenerationEntity.class, id);
            attempt.status("PROCESSING");
            attempt.touch(Clock.fixed(now, java.time.ZoneOffset.UTC));
            return null;
        });
        var before = service(actor, false, Clock.fixed(now.plusSeconds(59), java.time.ZoneOffset.UTC));
        var after = service(actor, false, Clock.fixed(now.plusSeconds(60), java.time.ZoneOffset.UTC));
        assertEquals("PROCESSING", database.inTransaction(() -> before.find(id).fold(GenerationEntity::status, () -> null)));
        assertEquals("UNKNOWN", database.inTransaction(() -> after.find(id).fold(GenerationEntity::status, () -> null)));
        assertEquals("UNKNOWN", readStatus(id));
        assertTrue(database.inTransaction(() -> service(UUID.randomUUID(), false, Clock.fixed(now.plusSeconds(61), java.time.ZoneOffset.UTC)).find(id)) instanceof dev.hustletech.loopmarket.shared.domain.Outcome.NotFound);
    }

    private String readStatus(UUID id) {
        try (var session = database.getSessionFactory().openSession()) {
            return session.find(GenerationEntity.class, id).status();
        }
    }

    private GenerationService service(UUID actor, boolean admin) {
        return service(actor, admin, Clock.systemUTC());
    }
    private GenerationService service(UUID actor, boolean admin, Clock clock) {
        return new GenerationService(new GenericHibernateRepository<>(database.getSessionFactory(), GenerationEntity.class,
                (builder, root) -> builder.equal(root.get("actorId"), actor)), clock, actor, admin, database.getSessionFactory());
    }
}
