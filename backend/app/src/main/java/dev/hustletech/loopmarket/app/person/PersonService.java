package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.ClientErrorException;
import org.hibernate.SessionFactory;
import dev.hustletech.loopmarket.shared.domain.Outcome;

// Keeps profile operations simple while preventing soft-deleting a person that still owns an account.
public final class PersonService extends GenericCrudService<PersonEntity, PersonService.Input, PersonService.Input> {
    public record Input(@NotBlank @Size(max = 120) String displayName) {}

    private final SessionFactory sessions;
    public PersonService(GenericRepository<PersonEntity> repository, Clock clock, SessionFactory sessions) {
        super(repository, clock); this.sessions = java.util.Objects.requireNonNull(sessions);
    }
    @Override protected PersonEntity createEntity(Input input) { return new PersonEntity(input.displayName().trim()); }
    @Override protected void updateEntity(PersonEntity entity, Input input) { entity.rename(input.displayName().trim()); }
    @Override public Outcome<PersonEntity> delete(UUID id) {
        PersonEntity person = repository.findActive(id).orElse(null);
        if (person == null) return new Outcome.NotFound<>();
        sessions.getCurrentSession().refresh(person, LockModeType.PESSIMISTIC_WRITE);
        if (person.isDeleted()) return new Outcome.NotFound<>();
        // Prevent orphaned identity links; account creation takes the same profile lock.
        boolean hasAccount = !sessions.getCurrentSession().createQuery(
                "select a.id from AccountEntity a where a.personId = :id and a.deletedAt is null", UUID.class)
                .setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_READ).setMaxResults(1).getResultList().isEmpty();
        if (hasAccount) throw new ClientErrorException("An active account references this person", 409);
        return super.delete(id);
    }
}
