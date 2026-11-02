package dev.hustletech.loopmarket.app.interest;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import dev.hustletech.loopmarket.app.person.PersonService;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountService;
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
// Checks that only published third-party listings accept one active interest per buyer.
class InterestPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(InterestEntity.class)
            .addEntityClass(PersonEntity.class).addEntityClass(AccountEntity.class).build();

    @Test void rejectsDraftAndOwnListingAndDeduplicatesActiveInterest() {
        var factory = database.getSessionFactory();
        var people = new PersonService(new GenericHibernateRepository<>(factory, PersonEntity.class), Clock.systemUTC(), factory);
        var accounts = new AccountService(new GenericHibernateRepository<>(factory, AccountEntity.class), Clock.systemUTC(), factory);
        UUID seller = database.inTransaction(() -> accounts.create(new AccountService.Input(people.create(new PersonService.Input("Seller")).getId(), "issuer", "seller", "USER")).getId());
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
        UUID other = database.inTransaction(() -> listings.create(new ListingService.Input("Other", null, null, null, null, null, null, null)).getId());
        database.inTransaction(() -> {
            database.getSessionFactory().getCurrentSession().createMutationQuery("update ListingEntity set status = 'PUBLISHED' where id = :id")
                    .setParameter("id", other).executeUpdate();
            return null;
        });
        ClientErrorException move = assertThrows(ClientErrorException.class,
                () -> database.inTransaction(() -> interests.update(first, new InterestService.Input(other))));
        assertEquals(409, move.getResponse().getStatus());
        assertEquals(listing, database.inTransaction(() -> interests.find(first).fold(InterestEntity::listingId, () -> null)));
        database.inTransaction(() -> accounts.delete(seller));
        ClientErrorException hidden = assertThrows(ClientErrorException.class,
                () -> database.inTransaction(() -> interests.create(new InterestService.Input(listing))));
        assertEquals(404, hidden.getResponse().getStatus());
    }

    private InterestService service(UUID buyer) {
        return new InterestService(new GenericHibernateRepository<>(database.getSessionFactory(), InterestEntity.class,
                (builder, root) -> builder.equal(root.get("buyerId"), buyer)), Clock.systemUTC(), buyer, database.getSessionFactory());
    }
}
