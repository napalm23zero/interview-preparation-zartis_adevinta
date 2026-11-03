package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.app.listing.ListingVisibility;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.hibernate.SessionFactory;
import java.time.Clock;
import java.util.UUID;

@Path("/catalog/{listingId}/photos")
public final class PublicPhotosResource {
    private final SessionFactory sessions;
    private final java.nio.file.Path storage;
    private final long maxBytes;
    public PublicPhotosResource(SessionFactory sessions, java.nio.file.Path storage, long maxBytes) {
        this.sessions = sessions; this.storage = storage; this.maxBytes = maxBytes;
    }
    private GenericHibernateRepository<PhotoEntity> repository(UUID listingId) {
        return new GenericHibernateRepository<>(sessions, PhotoEntity.class, (b, root) -> b.and(
                b.equal(root.get("listingId"), listingId), b.equal(root.get("validationStatus"), "VALID"),
                b.equal(root.get("listing").get("status"), "PUBLISHED"), b.isNull(root.get("listing").get("deletedAt"))));
    }
    @GET @Produces("application/json") @UnitOfWork
    public Response list(@PathParam("listingId") UUID listingId) {
        if (!ListingVisibility.isPublic(sessions.getCurrentSession(), listingId)) return Response.status(404).build();
        return Response.ok(repository(listingId).findAllActive(0, 5).stream().map(PhotoEntity::getId).toList()).build();
    }
    @GET @Path("/{photoId}") @UnitOfWork
    public Response content(@PathParam("listingId") UUID listingId, @PathParam("photoId") UUID photoId) {
        if (!ListingVisibility.isPublic(sessions.getCurrentSession(), listingId)) return Response.status(404).build();
        var service = new PhotoService(repository(listingId), Clock.systemUTC(), null, sessions, storage, maxBytes);
        return service.content(photoId).map(image -> Response.ok(image.bytes(), image.mediaType()).header("Cache-Control", "no-store").build())
                .orElseGet(() -> Response.status(404).build());
    }
}
