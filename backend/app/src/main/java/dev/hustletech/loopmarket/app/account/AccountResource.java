package dev.hustletech.loopmarket.app.account;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.UUID;

@Path("/users")
// Admin-only HTTP boundary for linking provider identities to local person profiles.
public final class AccountResource extends GenericCrudResource<AccountEntity, AccountService.Input, AccountService.Input, AccountResource.View> {
    public record View(UUID id, UUID personId, String issuer, String externalSubject, String role, Instant createdAt, Instant updatedAt) {}
    private final AccountService service;
    public AccountResource(AccountService service) { this.service = service; }

    // Only the administrator may alter identity bindings or grant authority.
    @Override protected GenericCrudService<AccountEntity, AccountService.Input, AccountService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        if (!security.isUserInRole("ADMIN")) throw new ForbiddenException();
        return service;
    }
    @Override protected View toResponse(AccountEntity entity) {
        return new View(entity.getId(), entity.personId(), entity.issuer(), entity.externalSubject(), entity.role(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
