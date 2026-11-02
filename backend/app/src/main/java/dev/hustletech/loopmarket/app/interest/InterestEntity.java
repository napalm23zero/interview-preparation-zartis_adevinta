package dev.hustletech.loopmarket.app.interest;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "interests")
// Persists one buyer's interest in one listing; the database unique key also guards duplicate attempts.
public class InterestEntity extends GenericEntity {
    @Column(name = "listing_id", nullable = false, columnDefinition = "BINARY(16)") private UUID listingId;
    @Column(name = "buyer_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID buyerId;

    protected InterestEntity() {}
    InterestEntity(UUID listingId, UUID buyerId) { this.listingId = listingId; this.buyerId = buyerId; }
    void moveTo(UUID listingId) { this.listingId = listingId; }
    UUID listingId() { return listingId; }
    UUID buyerId() { return buyerId; }
}
