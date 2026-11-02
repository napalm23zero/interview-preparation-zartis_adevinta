package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hustletech.loopmarket.app.ProblemResponses;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Path("/generations")
// Actor-scoped generation API; deterministic output is explicitly labelled and makes no model call.
public final class GenerationResource extends GenericCrudResource<GenerationEntity, GenerationService.Create, GenerationService.Update, GenerationResource.View> {
    public record View(UUID id, UUID listingId, UUID actorId, String requestKey, long inputVersion, String clientRevision,
                       String mode, String status, String provider, String model, java.util.Map<String, String> suggestions,
                       Instant createdAt, Instant updatedAt) {}
    private final SessionFactory sessions;
    private final GenerationRunner runner;
    public GenerationResource(SessionFactory sessions, String gatewayUrl, ObjectMapper json) {
        this.sessions = sessions;
        GatewayClient gateway = new GatewayClient(gatewayUrl, json);
        this.runner = new GenerationRunner(sessions, gateway::generate, Clock.systemUTC());
    }
    @Override protected GenericCrudService<GenerationEntity, GenerationService.Create, GenerationService.Update> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID actor = UUID.fromString(security.getUserPrincipal().getName());
        return new GenerationService(new GenericHibernateRepository<>(sessions, GenerationEntity.class,
                (builder, root) -> builder.equal(root.get("actorId"), actor)), Clock.systemUTC(), actor,
                security.isUserInRole("ADMIN"), sessions);
    }
    @Override protected View toResponse(GenerationEntity e) {
        return new View(e.getId(), e.listingId(), e.actorId(), e.requestKey(), e.inputVersion(), e.clientRevision(),
                e.mode(), e.status(), e.provider(), e.model(), e.suggestions(), e.getCreatedAt(), e.getUpdatedAt());
    }

    // No UnitOfWork here: the runner commits its claim before calling the gateway.
    @POST @Path("/{id}/execute") @Produces(MediaType.APPLICATION_JSON)
    public Response execute(@Context SecurityContext security, @PathParam("id") UUID id) {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        try {
            GenerationEntity attempt = runner.run(id, UUID.fromString(security.getUserPrincipal().getName()));
            return Response.status("PROCESSING".equals(attempt.status()) ? 202 : 200).entity(toResponse(attempt)).build();
        }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return ProblemResponses.gatewayUnavailable(); }
        catch (java.io.IOException exception) { return ProblemResponses.gatewayUnavailable(); }
    }
}
