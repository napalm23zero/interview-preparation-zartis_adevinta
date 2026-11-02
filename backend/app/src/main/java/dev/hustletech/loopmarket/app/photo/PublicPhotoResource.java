package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.app.listing.ListingVisibility;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.util.UUID;

// Public images follow listing visibility; no draft transmissions reach the Rebel spies.
@Path("/catalog/{listingId}/photo")
public final class PublicPhotoResource {
    private final SessionFactory sessions;
    private final java.nio.file.Path storage;
    private final long maxBytes;

    public PublicPhotoResource(SessionFactory sessions, java.nio.file.Path storage, long maxBytes) {
        this.sessions = sessions;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    @GET @UnitOfWork
    public Response content(@PathParam("listingId") UUID listingId) {
        if (!ListingVisibility.isPublic(sessions.getCurrentSession(), listingId)) return Response.status(404).build();
        // Apply the public scope twice, including at the file read, so deleted or archived rows stay hidden.
        var repository = new GenericHibernateRepository<>(sessions, PhotoEntity.class, (b, root) -> b.and(
                b.equal(root.get("listingId"), listingId), b.equal(root.get("validationStatus"), "VALID"),
                b.equal(root.get("listing").get("status"), "PUBLISHED"),
                b.isNull(root.get("listing").get("deletedAt"))));
        var photos = repository.findAllActive(0, 1);
        if (photos.isEmpty()) return Response.status(404).build();
        PhotoService service = new PhotoService(repository, Clock.systemUTC(), null, sessions, storage, maxBytes);
        return service.content(photos.get(0).getId())
                .map(image -> Response.ok(image.bytes(), image.mediaType()).header("Cache-Control", "no-store").build())
                .orElseGet(() -> Response.status(404).build());
    }
}
