package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountService;
import dev.hustletech.loopmarket.app.interest.InterestEntity;
import dev.hustletech.loopmarket.app.interest.InterestService;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import dev.hustletech.loopmarket.app.person.PersonService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class SellerResourceTest {
    final DAOTestExtension db = DAOTestExtension.newBuilder().addEntityClass(PersonEntity.class)
            .addEntityClass(AccountEntity.class).addEntityClass(ListingEntity.class).addEntityClass(InterestEntity.class).build();

    @Test void sellerSeesOnlyTheirOwnInterestsAndSafeIdentity() {
        var factory = db.getSessionFactory();
        var people = new PersonService(new GenericHibernateRepository<>(factory, PersonEntity.class), Clock.systemUTC(), factory);
        var accounts = new AccountService(new GenericHibernateRepository<>(factory, AccountEntity.class), Clock.systemUTC(), factory);
        UUID seller = db.inTransaction(() -> accounts.create(new AccountService.Input(people.create(new PersonService.Input("Vader")).getId(), "issuer", "vader", "USER")).getId());
        UUID buyer = db.inTransaction(() -> accounts.create(new AccountService.Input(people.create(new PersonService.Input("Luke")).getId(), "issuer", "luke", "USER")).getId());
        var listings = new ListingService(new GenericHibernateRepository<>(factory, ListingEntity.class), Clock.systemUTC(), seller, factory);
        UUID listing = db.inTransaction(() -> { var item = listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)); item.status("PUBLISHED"); return item.getId(); });
        var interests = new InterestService(new GenericHibernateRepository<>(factory, InterestEntity.class), Clock.systemUTC(), buyer, factory);
        db.inTransaction(() -> interests.create(new InterestService.Input(listing)));
        var resource = new SellerResource(factory);
        db.inTransaction(() -> {
            assertEquals("Vader", resource.me(security(seller)).displayName());
            assertEquals("Luke", resource.interests(security(seller), listing).get(0).buyerDisplayName());
            var sellerListing = resource.listings(security(seller), "PUBLISHED", 1, 10).content().get(0);
            assertEquals(listing, sellerListing.id());
            assertEquals(1, sellerListing.interestCount());
            assertEquals(listing, resource.listing(security(seller), listing).id());
            assertThrows(NotFoundException.class, () -> resource.listing(security(buyer), listing));
            assertEquals(0, resource.listings(security(seller), "DRAFT", 1, 10).total());
            assertEquals(0, resource.listings(security(buyer), "PUBLISHED", 1, 10).total());
            assertThrows(BadRequestException.class, () -> resource.listings(security(seller), "SECRET", 1, 10));
            assertThrows(NotFoundException.class, () -> resource.interests(security(buyer), listing));
            assertThrows(NotAuthorizedException.class, () -> resource.me(null));
            return null;
        });
    }

    private static SecurityContext security(UUID id) {
        return new SecurityContext() {
            public Principal getUserPrincipal() { return id::toString; }
            public boolean isUserInRole(String role) { return false; }
            public boolean isSecure() { return false; }
            public String getAuthenticationScheme() { return "Bearer"; }
        };
    }
}
