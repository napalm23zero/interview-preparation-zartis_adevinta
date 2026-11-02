package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Path("/photos")
// Exposes seller-owned photo metadata, upload, and validated content through separate HTTP operations.
public final class PhotoResource extends GenericCrudResource<PhotoEntity, PhotoService.Input, PhotoService.Input, PhotoResource.View> {
    public record View(UUID id, UUID listingId, String mediaType, long byteSize, String validationStatus, Instant createdAt, Instant updatedAt) {}
    private final SessionFactory sessions;
    private final java.nio.file.Path storage;
    private final long maxBytes;
    public PhotoResource(SessionFactory sessions, java.nio.file.Path storage, long maxBytes) { this.sessions = sessions; this.storage = storage; this.maxBytes = maxBytes; }
    @Override protected GenericCrudService<PhotoEntity, PhotoService.Input, PhotoService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        UUID owner = UUID.fromString(security.getUserPrincipal().getName());
        return new PhotoService(new GenericHibernateRepository<>(sessions, PhotoEntity.class,
                (builder, root) -> builder.and(builder.equal(root.get("listing").get("sellerId"), owner),
                        builder.isNull(root.get("listing").get("deletedAt")))), Clock.systemUTC(), owner, sessions, storage, maxBytes);
    }

    @PUT @Path("/{id}/content") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response upload(@Context SecurityContext security, @PathParam("id") UUID id, @Valid @NotNull PhotoService.Content content) {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        PhotoService service = (PhotoService) authorizedService(security, Action.UPDATE, id);
        return service.upload(id, content).fold(photo -> Response.ok(toResponse(photo)).build(), () -> Response.status(404).build());
    }

    @GET @Path("/{id}/content") @UnitOfWork
    public Response content(@Context SecurityContext security, @PathParam("id") UUID id) {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        PhotoService service = (PhotoService) authorizedService(security, Action.READ, id);
        return service.content(id).map(image -> Response.ok(image.bytes(), image.mediaType())
                .header("Cache-Control", "private, no-store").build()).orElseGet(() -> Response.status(404).build());
    }
    @Override protected View toResponse(PhotoEntity e) {
        return new View(e.getId(), e.listingId(), e.mediaType(), e.byteSize(), e.validationStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
