package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Path("/generations")
// Actor-scoped API for tracking generation attempts; it records requests without calling a model provider yet.
public final class GenerationResource extends GenericCrudResource<GenerationEntity, GenerationService.Create, GenerationService.Update, GenerationResource.View> {
    public record View(UUID id, UUID listingId, UUID actorId, String requestKey, long inputVersion, String clientRevision,
                       String mode, String status, Instant createdAt, Instant updatedAt) {}
    private final SessionFactory sessions;
    public GenerationResource(SessionFactory sessions) { this.sessions = sessions; }
    @Override protected GenericCrudService<GenerationEntity, GenerationService.Create, GenerationService.Update> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID actor = UUID.fromString(security.getUserPrincipal().getName());
        return new GenerationService(new GenericHibernateRepository<>(sessions, GenerationEntity.class,
                (builder, root) -> builder.equal(root.get("actorId"), actor)), Clock.systemUTC(), actor,
                security.isUserInRole("ADMIN"), sessions);
    }
    @Override protected View toResponse(GenerationEntity e) {
        return new View(e.getId(), e.listingId(), e.actorId(), e.requestKey(), e.inputVersion(), e.clientRevision(),
                e.mode(), e.status(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
