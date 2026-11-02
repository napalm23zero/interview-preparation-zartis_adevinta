package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import dev.hustletech.loopmarket.shared.domain.Outcome;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountService;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.ws.rs.ClientErrorException;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
// Exercises profile persistence, caller scoping, and account-safe soft deletion against MySQL semantics.
class PersonPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder().addEntityClass(PersonEntity.class).addEntityClass(AccountEntity.class).build();

    @Test void persistsUpdatesAndHidesSoftDeletedPerson() {
        PersonService service = new PersonService(new GenericHibernateRepository<>(database.getSessionFactory(), PersonEntity.class), Clock.systemUTC(), database.getSessionFactory());
        UUID id = database.inTransaction(() -> service.create(new PersonService.Input("Darth Vader")).getId());
        assertEquals(7, id.version());
        database.inTransaction(() -> {
            assertInstanceOf(Outcome.Success.class, service.update(id, new PersonService.Input("Anakin Skywalker")));
            return null;
        });
        database.inTransaction(() -> {
            assertEquals("Anakin Skywalker", service.find(id).fold(PersonEntity::displayName, () -> null));
            assertInstanceOf(Outcome.Success.class, service.delete(id));
            return null;
        });
        database.inTransaction(() -> {
            assertInstanceOf(Outcome.NotFound.class, service.find(id));
            assertEquals(0, service.count());
            return null;
        });
    }

    @Test void scopesIndividualReadsListsAndCounts() {
        PersonService all = new PersonService(new GenericHibernateRepository<>(database.getSessionFactory(), PersonEntity.class), Clock.systemUTC(), database.getSessionFactory());
        UUID hidden = database.inTransaction(() -> all.create(new PersonService.Input("Luke")).getId());
        UUID visible = database.inTransaction(() -> all.create(new PersonService.Input("Vader")).getId());
        PersonService scoped = new PersonService(new GenericHibernateRepository<>(database.getSessionFactory(), PersonEntity.class,
                (builder, root) -> builder.equal(root.get("displayName"), "Vader")), Clock.systemUTC(), database.getSessionFactory());
        database.inTransaction(() -> {
            assertInstanceOf(Outcome.NotFound.class, scoped.find(hidden));
            assertInstanceOf(Outcome.Success.class, scoped.find(visible));
            assertEquals(1, scoped.count());
            assertEquals(1, scoped.list(0, 10).size());
            return null;
        });
    }

    @Test void accountAndProfileRemainConsistentAcrossSoftDeletion() {
        PersonService people = new PersonService(new GenericHibernateRepository<>(database.getSessionFactory(), PersonEntity.class), Clock.systemUTC(), database.getSessionFactory());
        AccountService accounts = new AccountService(new GenericHibernateRepository<>(database.getSessionFactory(), AccountEntity.class), Clock.systemUTC(), database.getSessionFactory());
        UUID person = database.inTransaction(() -> people.create(new PersonService.Input("Ahsoka Tano")).getId());
        AccountService.Input input = new AccountService.Input(person, "https://identity.example", "subject", "USER");
        UUID account = database.inTransaction(() -> accounts.create(input).getId());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> people.delete(person)));
        database.inTransaction(() -> { accounts.delete(account); });
        database.inTransaction(() -> { people.delete(person); });
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> accounts.create(input)));
    }
}
