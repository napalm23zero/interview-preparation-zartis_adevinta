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
// Common identity and audit fields keep every persisted model on the same timeline.
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

    // UUIDv7 keeps epoch milliseconds in its high bits, so IDs sort roughly by creation time.
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
        // The entity owns UTC audit time; clients do not get to rewrite history.
        updatedAt = clock.instant();
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public final void useClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public final void touch(Clock clock) {
        useClock(clock);
        updatedAt = this.clock.instant();
    }

    // Retain the row for references and audit while repositories hide it from active reads.
    public final void softDelete(Clock clock) {
        useClock(clock);
        Instant now = this.clock.instant();
        deletedAt = now;
        updatedAt = now;
    }
}
