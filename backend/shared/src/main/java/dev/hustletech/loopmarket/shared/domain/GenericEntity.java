package dev.hustletech.loopmarket.shared.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class GenericEntity {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant deletedAt;

    @Transient
    private Clock clock = Clock.systemUTC();

    protected GenericEntity() {}

    protected GenericEntity(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    // UUIDv7 places time first; even the Sith appreciate an orderly timeline.
    @PrePersist
    protected void onCreate() {
        if (id != null) throw new IllegalStateException("New entity must not have an id");
        Instant now = clock.instant();
        long millis = now.toEpochMilli();
        if (millis < 0 || millis > 0xffffffffffffL) throw new IllegalStateException("Clock is outside UUIDv7 range");
        UUID random = UUID.randomUUID();
        id = new UUID((millis << 16) | 0x7000L | (random.getMostSignificantBits() & 0x0fffL), random.getLeastSignificantBits());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = clock.instant();
    }

    public final UUID getId() { return id; }
    public final Instant getCreatedAt() { return createdAt; }
    public final Instant getUpdatedAt() { return updatedAt; }
    public final Instant getDeletedAt() { return deletedAt; }
    public final boolean isDeleted() { return deletedAt != null; }

    public final void useClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public final void touch(Clock clock) {
        useClock(clock);
        updatedAt = this.clock.instant();
    }

    // Keep the record in the archives while hiding it from active reads.
    public final void softDelete(Clock clock) {
        useClock(clock);
        Instant now = this.clock.instant();
        deletedAt = now;
        updatedAt = now;
    }
}
