package dev.hustletech.loopmarket.shared;

import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

// Verifies shared UUID, UTC audit, update, and soft-delete behavior without needing a database.
class GenericCrudServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MemoryRepository repository = new MemoryRepository();
    private final GenericCrudService<TestEntity, String, String> service = new GenericCrudService<>(repository, clock) {
        protected TestEntity createEntity(String input) { return new TestEntity(input); }
        protected void updateEntity(TestEntity entity, String input) { entity.value = input; }
    };

    @Test void createsUuidV7AndUtcAuditFields() {
        TestEntity entity = service.create("first");
        assertEquals(7, entity.getId().version());
        assertEquals(2, entity.getId().variant());
        assertEquals(NOW.toEpochMilli(), entity.getId().getMostSignificantBits() >>> 16);
        assertEquals(NOW, entity.getCreatedAt());
        assertEquals(NOW, entity.getUpdatedAt());
        assertNull(entity.getDeletedAt());
    }

    @Test void updatesAndSoftDeletesWithoutReturningDeletedRecords() {
        TestEntity entity = service.create("first");
        assertInstanceOf(Outcome.Success.class, service.update(entity.getId(), "second"));
        assertEquals("second", entity.value);
        assertInstanceOf(Outcome.Success.class, service.delete(entity.getId()));
        assertEquals(NOW, entity.getDeletedAt());
        assertInstanceOf(Outcome.NotFound.class, service.find(entity.getId()));
        assertInstanceOf(Outcome.NotFound.class, service.update(entity.getId(), "third"));
        assertInstanceOf(Outcome.NotFound.class, service.delete(entity.getId()));
        assertEquals(0, service.count());
        assertTrue(service.list(0, 10).isEmpty());
    }

    @Test void auditTimesMatchDatabaseMicrosecondPrecision() {
        Instant precise = Instant.parse("2026-01-02T03:04:05.123456789Z");
        Instant stored = Instant.parse("2026-01-02T03:04:05.123456Z");
        Clock preciseClock = Clock.fixed(precise, ZoneOffset.UTC);
        var entity = new TestEntity("test");
        entity.useClock(preciseClock);
        entity.persist();
        assertEquals(stored, entity.getCreatedAt());
        assertEquals(stored, entity.getUpdatedAt());
        entity.touch(preciseClock);
        entity.softDelete(preciseClock);
        assertEquals(stored, entity.getUpdatedAt());
        assertEquals(stored, entity.getDeletedAt());
    }

    private static final class TestEntity extends GenericEntity {
        private String value;
        TestEntity(String value) { this.value = value; }
        void persist() { onCreate(); }
    }

    private static final class MemoryRepository implements GenericRepository<TestEntity> {
        private final Map<UUID, TestEntity> entities = new LinkedHashMap<>();
        public TestEntity create(TestEntity entity) { entity.persist(); entities.put(entity.getId(), entity); return entity; }
        public Optional<TestEntity> findActive(UUID id) { return Optional.ofNullable(entities.get(id)).filter(e -> !e.isDeleted()); }
        public List<TestEntity> findAllActive(int offset, int limit) {
            return entities.values().stream().filter(e -> !e.isDeleted()).skip(offset).limit(limit).toList();
        }
        public long countActive() { return entities.values().stream().filter(e -> !e.isDeleted()).count(); }
    }
}
