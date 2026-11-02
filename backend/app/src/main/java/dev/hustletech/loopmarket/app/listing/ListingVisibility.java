package dev.hustletech.loopmarket.app.listing;

import org.hibernate.Session;
import java.util.UUID;

// A public listing needs an active seller and profile; hidden accounts do not broadcast to the galaxy.
public final class ListingVisibility {
    private ListingVisibility() {}

    public static boolean isPublic(Session session, UUID id) {
        return session.createQuery("select count(l) from ListingEntity l, AccountEntity a, PersonEntity p " +
                "where l.id = :id and l.status = 'PUBLISHED' and l.deletedAt is null " +
                "and l.sellerId = a.id and a.deletedAt is null and a.personId = p.id and p.deletedAt is null", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }
}
