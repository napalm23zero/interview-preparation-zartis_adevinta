package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.ClientErrorException;
import jakarta.persistence.LockModeType;
import org.hibernate.SessionFactory;
import dev.hustletech.loopmarket.shared.domain.Outcome;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

public final class ListingService extends GenericCrudService<ListingEntity, ListingService.Input, ListingService.Input> {
    public record Input(@Size(max = 160) String title, @Size(max = 10000) String description,
                        @Size(max = 80) String category, @Size(max = 80) String condition,
                        @Size(max = 120) String city, @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
                        @Pattern(regexp = "[A-Z]{3}") String currency, Long expectedVersion) {}
    private final UUID seller;
    private final SessionFactory sessions;
    private final Clock clock;

    public ListingService(GenericRepository<ListingEntity> repository, Clock clock, UUID seller, SessionFactory sessions) {
        super(repository, clock); this.seller = seller; this.sessions = sessions; this.clock = clock;
    }
    @Override protected ListingEntity createEntity(Input input) {
        ListingEntity listing = new ListingEntity(seller);
        listing.update(input);
        return listing;
    }
    @Override protected void updateEntity(ListingEntity listing, Input input) {
        if (input.expectedVersion() == null || input.expectedVersion() != listing.version()) {
            throw new ClientErrorException("Listing version does not match", 409);
        }
        if ("ARCHIVED".equals(listing.status())) {
            throw new ClientErrorException("Archived listings cannot be updated", 409);
        }
        if ("PUBLISHED".equals(listing.status())) {
            if (blank(input.title()) || blank(input.description()) || blank(input.category()) || blank(input.condition())
                    || blank(input.city()) || input.price() == null || blank(input.currency())) {
                throw new ClientErrorException("Listing details are incomplete", 409);
            }
            requirePhoto(listing.getId());
        }
        listing.update(input);
    }

    public Outcome<ListingEntity> transition(UUID id, long expectedVersion, boolean publish) {
        return repository.findActive(id).<Outcome<ListingEntity>>map(listing -> {
            sessions.getCurrentSession().refresh(listing, LockModeType.PESSIMISTIC_WRITE);
            if (listing.isDeleted() || !seller.equals(listing.sellerId())) return new Outcome.NotFound<>();
            if (listing.version() != expectedVersion) throw new ClientErrorException("Listing version does not match", 409);
            if (publish) {
                if (!"DRAFT".equals(listing.status())) throw new ClientErrorException("Only drafts can be published", 409);
                if (blank(listing.title()) || blank(listing.description()) || blank(listing.category()) || blank(listing.condition())
                        || blank(listing.city()) || listing.price() == null || blank(listing.currency())) {
                    throw new ClientErrorException("Listing details are incomplete", 409);
                }
                requirePhoto(id);
                listing.status("PUBLISHED");
            } else {
                if (!"PUBLISHED".equals(listing.status())) throw new ClientErrorException("Only published listings can be archived", 409);
                listing.status("ARCHIVED");
            }
            listing.touch(clock);
            repository.flush();
            return new Outcome.Success<>(listing);
        }).orElseGet(Outcome.NotFound::new);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private void requirePhoto(UUID id) {
        Long photos = sessions.getCurrentSession().createQuery(
                "select count(p) from PhotoEntity p where p.listingId = :id and p.deletedAt is null and p.validationStatus = 'VALID'", Long.class)
                .setParameter("id", id).getSingleResult();
        if (photos == 0) throw new ClientErrorException("A validated photo is required", 409);
    }
}
