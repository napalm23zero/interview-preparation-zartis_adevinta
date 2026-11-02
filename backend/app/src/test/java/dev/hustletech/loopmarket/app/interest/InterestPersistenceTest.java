package dev.hustletech.loopmarket.app.interest;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class InterestPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(InterestEntity.class).build();

    @Test void rejectsDraftAndOwnListingAndDeduplicatesActiveInterest() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class),
                Clock.systemUTC(), seller, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        InterestService interests = service(buyer);
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> interests.create(new InterestService.Input(listing))));
        database.inTransaction(() -> {
            database.getSessionFactory().getCurrentSession().createMutationQuery("update ListingEntity set status = 'PUBLISHED' where id = :id")
                    .setParameter("id", listing).executeUpdate();
            return null;
        });
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> service(seller).create(new InterestService.Input(listing))));
        UUID first = database.inTransaction(() -> interests.create(new InterestService.Input(listing)).getId());
        UUID second = database.inTransaction(() -> interests.create(new InterestService.Input(listing)).getId());
        assertEquals(first, second);
    }

    private InterestService service(UUID buyer) {
        return new InterestService(new GenericHibernateRepository<>(database.getSessionFactory(), InterestEntity.class,
                (builder, root) -> builder.equal(root.get("buyerId"), buyer)), Clock.systemUTC(), buyer, database.getSessionFactory());
    }
}
