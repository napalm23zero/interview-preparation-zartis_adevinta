package dev.hustletech.loopmarket.app.account;

import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.util.UUID;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.ClientErrorException;
import org.hibernate.SessionFactory;

public final class AccountService extends GenericCrudService<AccountEntity, AccountService.Input, AccountService.Input> {
    public record Input(@NotNull UUID personId, @NotBlank @Size(max = 255) String issuer,
                        @NotBlank @Size(max = 255) String externalSubject,
                        @NotNull @Pattern(regexp = "USER|ADMIN") String role) {}

    private final SessionFactory sessions;
    public AccountService(GenericRepository<AccountEntity> repository, Clock clock, SessionFactory sessions) {
        super(repository, clock); this.sessions = java.util.Objects.requireNonNull(sessions);
    }
    @Override protected AccountEntity createEntity(Input input) {
        requireActivePerson(input.personId());
        return new AccountEntity(input.personId(), input.issuer(), input.externalSubject(), input.role());
    }
    @Override protected void updateEntity(AccountEntity entity, Input input) {
        requireActivePerson(input.personId());
        entity.update(input.personId(), input.issuer(), input.externalSubject(), input.role());
    }
    private void requireActivePerson(UUID id) {
        // Keep the account and profile aligned, even when the dark side races a deletion.
        PersonEntity person = sessions.getCurrentSession().find(PersonEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (person == null || person.isDeleted()) throw new ClientErrorException("Active person not found", 409);
    }
}
