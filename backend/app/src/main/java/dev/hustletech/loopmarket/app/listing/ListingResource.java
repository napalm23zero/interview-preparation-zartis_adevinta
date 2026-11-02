package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.ClientErrorException;
import jakarta.persistence.LockModeType;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json;
    public ListingResource(SessionFactory sessions) { this(sessions, null); }
    public ListingResource(SessionFactory sessions, ObjectMapper json) { this.sessions = sessions; this.json = json; }

    @Override @POST @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response create(@Context SecurityContext security, @Context jakarta.ws.rs.core.UriInfo uri, @Context jakarta.ws.rs.core.HttpHeaders headers, @Valid @NotNull ListingService.Input input) {
        try { return createDraft(security, uri, headers.getHeaderString("Idempotency-Key"), input); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Could not record draft request", exception); }
    }
    private Response createDraft(SecurityContext security, jakarta.ws.rs.core.UriInfo uri,
                                 String key, ListingService.Input input) throws Exception {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        validateKey(key);
        UUID actor = UUID.fromString(security.getUserPrincipal().getName());
        var session = sessions.getCurrentSession();
        // Serialize this seller's retry keys before looking them up; no duplicate drafts escape the fleet.
        lockActor(actor);
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));
        ListingRequestEntity prior = findRequest(actor, "CREATE", key);
        if (prior != null) {
            if (!prior.fingerprint().equals(fingerprint)) throw new ClientErrorException("Idempotency-Key conflicts with an existing request", 409);
            return Response.created(uri.getBaseUriBuilder().path("listings").path(prior.listingId().toString()).build())
                    .entity(prior.responseJson()).build();
        }
        ListingEntity listing = authorizedService(security, Action.CREATE, null).create(input);
        String response = json.writeValueAsString(toResponse(listing));
        session.persist(new ListingRequestEntity(actor, "CREATE", key, fingerprint, listing.getId(), response));
        session.flush();
        return Response.created(uri.getBaseUriBuilder().path("listings").path(listing.getId().toString()).build()).entity(response).build();
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._~-]+"))
            throw new BadRequestException("Idempotency-Key must contain 1-128 URL-safe characters");
    }
    private void lockActor(UUID actor) {
        AccountEntity account = sessions.getCurrentSession().find(AccountEntity.class, actor, LockModeType.PESSIMISTIC_WRITE);
        if (account == null || account.isDeleted()) throw new NotAuthorizedException("Bearer");
    }
    private ListingRequestEntity findRequest(UUID actor, String operation, String key) {
        return sessions.getCurrentSession().createQuery(
                "from ListingRequestEntity where actorId = :actor and operation = :operation and requestKey = :key", ListingRequestEntity.class)
                .setParameter("actor", actor).setParameter("operation", operation).setParameter("key", key).uniqueResult();
    }

    // Every query stays within the seller's territory; the dark side respects ownership.
    @Override protected GenericCrudService<ListingEntity, ListingService.Input, ListingService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID owner = UUID.fromString(security.getUserPrincipal().getName());
        return new ListingService(new GenericHibernateRepository<>(sessions, ListingEntity.class,
                (builder, root) -> builder.equal(root.get("sellerId"), owner)), Clock.systemUTC(), owner, sessions);
    }

    @POST @Path("/{id}/publications") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response publish(@Context SecurityContext security, @PathParam("id") UUID id,
                            @HeaderParam("Idempotency-Key") String key, @Valid @NotNull Transition transition) {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        validateKey(key);
        UUID actor = UUID.fromString(security.getUserPrincipal().getName());
        lockActor(actor);
        String fingerprint = id + ":" + transition.expectedVersion();
        ListingRequestEntity prior = findRequest(actor, "PUBLISH", key);
        if (prior != null) {
            if (!prior.fingerprint().equals(fingerprint)) throw new ClientErrorException("Idempotency-Key conflicts with an existing request", 409);
            return Response.ok(prior.responseJson(), MediaType.APPLICATION_JSON).build();
        }
        ListingService service = (ListingService) authorizedService(security, Action.UPDATE, id);
        return service.transition(id, transition.expectedVersion(), true).fold(listing -> {
            try {
                String response = json.writeValueAsString(toResponse(listing));
                sessions.getCurrentSession().persist(new ListingRequestEntity(actor, "PUBLISH", key, fingerprint, id, response));
                sessions.getCurrentSession().flush();
                return Response.ok(response, MediaType.APPLICATION_JSON).build();
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Could not record publication request", exception);
            }
        }, () -> Response.status(404).build());
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
