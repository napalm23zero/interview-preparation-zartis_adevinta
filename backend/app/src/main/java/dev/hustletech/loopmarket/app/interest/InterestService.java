package dev.hustletech.loopmarket.app.interest;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingVisibility;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.ClientErrorException;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.util.UUID;

// Applies buyer rules and serializes interest creation against listing archival.
public final class InterestService extends GenericCrudService<InterestEntity, InterestService.Input, InterestService.Input> {
    public record Input(@NotNull UUID listingId) {}
    private final UUID buyer;
    private final SessionFactory sessions;
    public InterestService(GenericRepository<InterestEntity> repository, Clock clock, UUID buyer, SessionFactory sessions) {
        super(repository, clock); this.buyer = buyer; this.sessions = sessions;
    }
    @Override public InterestEntity create(Input input) {
        requireAvailableListing(input.listingId());
        // Active retries are idempotent; deleted interests keep their unique key reserved.
        InterestEntity existing = sessions.getCurrentSession().createQuery(
                "from InterestEntity where listingId = :listing and buyerId = :buyer", InterestEntity.class)
                .setParameter("listing", input.listingId()).setParameter("buyer", buyer).uniqueResult();
        if (existing != null) {
            if (existing.isDeleted()) throw new ClientErrorException("Interest has been deleted", 409);
            return existing;
        }
        return super.create(input);
    }
    @Override protected InterestEntity createEntity(Input input) {
        requireAvailableListing(input.listingId());
        return new InterestEntity(input.listingId(), buyer);
    }
    @Override protected void updateEntity(InterestEntity entity, Input input) {
        // Interest records describe one buyer's original signal; they cannot defect to another listing.
        if (!entity.listingId().equals(input.listingId())) throw new ClientErrorException("Interest listing cannot be changed", 409);
        requireAvailableListing(input.listingId());
    }
    private void requireAvailableListing(UUID id) {
        // Lock the listing so archiving cannot race a buyer's interest, even at lightspeed.
        ListingEntity listing = sessions.getCurrentSession().find(ListingEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (listing == null) throw new ClientErrorException("Listing is unavailable", 404);
        sessions.getCurrentSession().refresh(listing, LockModeType.PESSIMISTIC_WRITE);
        if (listing.isDeleted() || !"PUBLISHED".equals(listing.getStatus()) || !ListingVisibility.isPublic(sessions.getCurrentSession(), id)) {
            throw new ClientErrorException("Listing is unavailable", 404);
        }
        if (listing.getSellerId().equals(buyer)) throw new ClientErrorException("Cannot express interest in your own listing", 403);
    }
}
