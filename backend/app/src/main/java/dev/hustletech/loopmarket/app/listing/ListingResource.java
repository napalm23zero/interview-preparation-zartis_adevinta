package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Path("/listings")
// Seller-scoped listing API plus explicit publish/archive transitions for the listing lifecycle.
public final class ListingResource extends GenericCrudResource<ListingEntity, ListingService.Input, ListingService.Input, ListingResource.View> {
    public record View(UUID id, UUID sellerId, String title, String description, String category, String condition,
                       String city, BigDecimal price, String currency, String status, long version, Instant createdAt, Instant updatedAt) {}
    public record Transition(@NotNull @PositiveOrZero Long expectedVersion) {}
    private final SessionFactory sessions;
    public ListingResource(SessionFactory sessions) { this.sessions = sessions; }

    // Every query stays within the seller's territory; the dark side respects ownership.
    @Override protected GenericCrudService<ListingEntity, ListingService.Input, ListingService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID owner = UUID.fromString(security.getUserPrincipal().getName());
        return new ListingService(new GenericHibernateRepository<>(sessions, ListingEntity.class,
                (builder, root) -> builder.equal(root.get("sellerId"), owner)), Clock.systemUTC(), owner, sessions);
    }

    @POST @Path("/{id}/publications") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response publish(@Context SecurityContext security, @PathParam("id") UUID id, @Valid @NotNull Transition transition) {
        return transition(security, id, transition, true);
    }

    @POST @Path("/{id}/archives") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response archive(@Context SecurityContext security, @PathParam("id") UUID id, @Valid @NotNull Transition transition) {
        return transition(security, id, transition, false);
    }

    private Response transition(SecurityContext security, UUID id, Transition transition, boolean publish) {
        // Reuse the same owner-scoped service as CRUD; lifecycle actions do not bypass authorization.
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        ListingService service = (ListingService) authorizedService(security, Action.UPDATE, id);
        return service.transition(id, transition.expectedVersion(), publish).fold(
                listing -> Response.ok(toResponse(listing)).build(), () -> Response.status(404).build());
    }
    @Override protected View toResponse(ListingEntity e) {
        return new View(e.getId(), e.sellerId(), e.title(), e.description(), e.category(), e.condition(), e.city(),
                e.price(), e.currency(), e.status(), e.version(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
