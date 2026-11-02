package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.domain.Outcome;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class ListingPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder().addEntityClass(ListingEntity.class).build();

    @Test void ownerScopeAndOptimisticVersionAreEnforced() {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ListingService owner = service(seller);
        ListingService stranger = service(other);
        UUID id = database.inTransaction(() -> owner.create(input("Holocron", null)).getId());
        database.inTransaction(() -> {
            assertInstanceOf(Outcome.NotFound.class, stranger.find(id));
            assertInstanceOf(Outcome.NotFound.class, stranger.update(id, input("Stolen", 0L)));
            assertEquals(0, stranger.count());
            return null;
        });
        long version = database.inTransaction(() -> owner.update(id, input("Sith holocron", 0L))
                .fold(ListingEntity::version, () -> -1L));
        assertEquals(1, version);
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> owner.update(id, input("Stale", 0L))));
    }

    private ListingService service(UUID owner) {
        return new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class,
                (builder, root) -> builder.equal(root.get("sellerId"), owner)), Clock.systemUTC(), owner, database.getSessionFactory());
    }
    private ListingService.Input input(String title, Long version) {
        return new ListingService.Input(title, null, null, null, null, null, null, version);
    }
}
