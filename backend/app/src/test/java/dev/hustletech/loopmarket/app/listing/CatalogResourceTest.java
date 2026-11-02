package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import dev.hustletech.loopmarket.app.person.PersonService;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountService;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class CatalogResourceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder().addEntityClass(ListingEntity.class)
            .addEntityClass(AccountEntity.class).addEntityClass(PersonEntity.class).build();

    @Test void publicCatalogHidesDraftsArchivedAndDeletedAndAppliesFilters() {
        var repo = new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class);
        var catalog = new CatalogResource(database.getSessionFactory());
        var people = new PersonService(new GenericHibernateRepository<>(database.getSessionFactory(), PersonEntity.class), Clock.systemUTC(), database.getSessionFactory());
        var accounts = new AccountService(new GenericHibernateRepository<>(database.getSessionFactory(), AccountEntity.class), Clock.systemUTC(), database.getSessionFactory());
        UUID seller = database.inTransaction(() -> accounts.create(new AccountService.Input(people.create(new PersonService.Input("Vader")).getId(), "issuer", "vader", "USER")).getId());
        UUID[] ids = database.inTransaction(() -> {
            ListingEntity visible = listing("Holocron", "PUBLISHED", seller); repo.create(visible);
            ListingEntity draft = listing("Draft", "DRAFT", seller); repo.create(draft);
            ListingEntity archived = listing("Archived", "ARCHIVED", seller); repo.create(archived);
            ListingEntity deleted = listing("Deleted", "PUBLISHED", seller); repo.create(deleted);
            repo.flush(); deleted.softDelete(Clock.systemUTC());
            return new UUID[]{visible.getId(), draft.getId(), archived.getId(), deleted.getId()};
        });
        database.inTransaction(() -> {
            var page = catalog.list(1, 10, "holo", "BOOKS", null, "Montreal", BigDecimal.ONE, BigDecimal.TEN);
            assertEquals(1, page.total());
            assertEquals(ids[0], page.content().get(0).id());
            assertEquals("Vader", page.content().get(0).sellerDisplayName());
            assertEquals(ids[0], catalog.find(ids[0]).id());
            for (int i = 1; i < ids.length; i++) {
                UUID hidden = ids[i];
                assertThrows(NotFoundException.class, () -> catalog.find(hidden));
            }
            assertEquals(0, catalog.list(1, 10, "%", null, null, null, null, null).total());
            assertThrows(BadRequestException.class, () -> catalog.list(0, 10, null, null, null, null, null, null));
            assertThrows(BadRequestException.class, () -> catalog.list(1, 10, null, "INVALID", null, null, null, null));
            assertEquals(1, catalog.list(1, 10, null, null, null, null, new BigDecimal("10.000"), new BigDecimal("9999999999.99")).total());
            assertThrows(BadRequestException.class, () -> catalog.list(1, 10, null, null, null, null, new BigDecimal("0.001"), null));
            return null;
        });
        database.inTransaction(() -> { accounts.delete(seller); return null; });
        database.inTransaction(() -> {
            assertEquals(0, catalog.list(1, 10, null, null, null, null, null, null).total());
            assertThrows(NotFoundException.class, () -> catalog.find(ids[0]));
            return null;
        });
    }

    private ListingEntity listing(String title, String status, UUID seller) {
        ListingEntity item = new ListingEntity(seller);
        item.update(new ListingService.Input(title, "A rare item", "BOOKS", "USED", "Montreal", BigDecimal.TEN, "CAD", null));
        item.status(status);
        return item;
    }
}
