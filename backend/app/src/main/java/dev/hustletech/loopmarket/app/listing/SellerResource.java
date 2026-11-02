package dev.hustletech.loopmarket.app.listing;

import io.dropwizard.hibernate.UnitOfWork;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Seller views derive their scope from the session; a supplied UUID is never an imperial badge.
@Path("/users/me")
@Produces(MediaType.APPLICATION_JSON)
public final class SellerResource {
    public record Identity(UUID id, String displayName, String role) {}
    public record Interest(String buyerDisplayName, Instant createdAt) {}
    public record SellerListing(UUID id, UUID sellerId, String title, String description, String category, String condition,
                                String city, java.math.BigDecimal price, String currency, String status, long version,
                                Instant createdAt, Instant updatedAt, long interestCount) {}
    private final SessionFactory sessions;

    public SellerResource(SessionFactory sessions) { this.sessions = sessions; }

    @GET @UnitOfWork
    public Identity me(@Context SecurityContext security) {
        UUID actor = actor(security);
        var rows = sessions.getCurrentSession().createQuery(
                "select p.displayName, a.role from AccountEntity a, PersonEntity p " +
                "where a.id = :id and a.personId = p.id and a.deletedAt is null and p.deletedAt is null", Object[].class)
                .setParameter("id", actor).setMaxResults(1).getResultList();
        if (rows.isEmpty()) throw new NotAuthorizedException("Bearer");
        return new Identity(actor, (String) rows.get(0)[0], (String) rows.get(0)[1]);
    }

    @GET @Path("/listings") @UnitOfWork
    public GenericCrudResource.Page<SellerListing> listings(@Context SecurityContext security,
            @QueryParam("status") String status, @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        UUID seller = actor(security);
        if (page < 1 || size < 1 || (status != null && !List.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status))) {
            throw new BadRequestException("Invalid status or pagination");
        }
        size = Math.min(size, 50);
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new BadRequestException("Page is out of range");
        // Count and content share the same seller scope; even a Sith cannot list a rival's drafts.
        var repository = new GenericHibernateRepository<>(sessions, ListingEntity.class, (builder, root) -> {
            var owner = builder.equal(root.get("sellerId"), seller);
            return status == null ? owner : builder.and(owner, builder.equal(root.get("status"), status));
        });
        long total = repository.countActive();
        if (page > 1 && offset >= total) throw new BadRequestException("Page is out of range");
        var items = repository.findAllActive((int) offset, size);
        var counts = new java.util.HashMap<UUID, Long>();
        if (!items.isEmpty()) {
            // Match the visible buyer list in one grouped query; no N+1 patrols through the database.
            sessions.getCurrentSession().createQuery(
                    "select i.listingId, count(i) from InterestEntity i, AccountEntity a, PersonEntity p " +
                    "where i.listingId in :ids and i.buyerId = a.id and a.personId = p.id " +
                    "and i.deletedAt is null and a.deletedAt is null and p.deletedAt is null group by i.listingId", Object[].class)
                    .setParameter("ids", items.stream().map(ListingEntity::getId).toList()).getResultList()
                    .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        }
        var resource = new ListingResource(sessions);
        var content = items.stream().map(item -> {
            var view = resource.toResponse(item);
            return new SellerListing(view.id(), view.sellerId(), view.title(), view.description(), view.category(), view.condition(),
                    view.city(), view.price(), view.currency(), view.status(), view.version(), view.createdAt(), view.updatedAt(),
                    counts.getOrDefault(view.id(), 0L));
        }).toList();
        return new GenericCrudResource.Page<>(content, page, size, total);
    }

    @GET @Path("/listings/{listingId}") @UnitOfWork
    public ListingResource.View listing(@Context SecurityContext security, @PathParam("listingId") UUID listingId) {
        UUID seller = actor(security);
        var repository = new GenericHibernateRepository<>(sessions, ListingEntity.class,
                (builder, root) -> builder.equal(root.get("sellerId"), seller));
        return repository.findActive(listingId).map(new ListingResource(sessions)::toResponse)
                .orElseThrow(NotFoundException::new);
    }

    @GET @Path("/listings/{listingId}/interests") @UnitOfWork
    public List<Interest> interests(@Context SecurityContext security, @PathParam("listingId") UUID listingId) {
        UUID actor = actor(security);
        Long owned = sessions.getCurrentSession().createQuery(
                "select count(l) from ListingEntity l where l.id = :id and l.sellerId = :seller and l.deletedAt is null", Long.class)
                .setParameter("id", listingId).setParameter("seller", actor).getSingleResult();
        if (owned == 0) throw new NotFoundException();
        // Buyer display names are sufficient for the MVP; provider subjects and contact details stay private.
        return sessions.getCurrentSession().createQuery(
                "select p.displayName, i.createdAt from InterestEntity i, AccountEntity a, PersonEntity p " +
                "where i.listingId = :listing and i.buyerId = a.id and a.personId = p.id " +
                "and i.deletedAt is null and a.deletedAt is null and p.deletedAt is null order by i.createdAt desc, i.id asc", Object[].class)
                .setParameter("listing", listingId).getResultList().stream()
                .map(row -> new Interest((String) row[0], (Instant) row[1])).toList();
    }

    private static UUID actor(SecurityContext security) {
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        return UUID.fromString(security.getUserPrincipal().getName());
    }
}
