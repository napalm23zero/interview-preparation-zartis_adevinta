package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class GenerationPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(GenerationEntity.class).build();

    @Test void requestKeyIsIdempotentAndCannotBeReusedForDifferentInput() {
        UUID actor = UUID.randomUUID();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), actor, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        GenerationService generations = service(actor, false);
        GenerationService.Create input = new GenerationService.Create(listing, "key-1", 0L, "revision-1", "TEXT");
        UUID id = database.inTransaction(() -> generations.create(input).getId());
        assertEquals(id, database.inTransaction(() -> generations.create(input).getId()));
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> generations.create(
                new GenerationService.Create(listing, "key-1", 0L, "revision-2", "TEXT"))));
        assertThrows(ForbiddenException.class, () -> database.inTransaction(() -> generations.update(id, new GenerationService.Update("FAILED"))));
        assertEquals(0L, database.inTransaction(() -> service(UUID.randomUUID(), false).count()).longValue());
    }

    private GenerationService service(UUID actor, boolean admin) {
        return new GenerationService(new GenericHibernateRepository<>(database.getSessionFactory(), GenerationEntity.class,
                (builder, root) -> builder.equal(root.get("actorId"), actor)), Clock.systemUTC(), actor, admin, database.getSessionFactory());
    }
}
