package dev.hustletech.loopmarket.app.interest;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Path("/interests")
// Buyer-scoped API: repository filtering makes other buyers' interest rows invisible, no Force tricks required.
public final class InterestResource extends GenericCrudResource<InterestEntity, InterestService.Input, InterestService.Input, InterestResource.View> {
    public record View(UUID id, UUID listingId, UUID buyerId, Instant createdAt, Instant updatedAt) {}
    private final SessionFactory sessions;
    public InterestResource(SessionFactory sessions) { this.sessions = sessions; }

    @Override protected GenericCrudService<InterestEntity, InterestService.Input, InterestService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID buyer = UUID.fromString(security.getUserPrincipal().getName());
        return new InterestService(new GenericHibernateRepository<>(sessions, InterestEntity.class,
                (builder, root) -> builder.equal(root.get("buyerId"), buyer)), Clock.systemUTC(), buyer, sessions);
    }
    @Override protected View toResponse(InterestEntity e) {
        return new View(e.getId(), e.listingId(), e.buyerId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
