package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.UUID;

@Path("/persons")
public final class PersonResource extends GenericCrudResource<PersonEntity, PersonService.Input, PersonService.Input, PersonResource.View> {
    public record View(UUID id, String displayName, Instant createdAt, Instant updatedAt) {}
    private final PersonService service;

    public PersonResource(PersonService service) { this.service = service; }

    // Identity administration stays in the hands of trusted officers.
    @Override protected GenericCrudService<PersonEntity, PersonService.Input, PersonService.Input> authorizedService(
            SecurityContext security, Action action, UUID id) {
        if (!security.isUserInRole("ADMIN")) throw new ForbiddenException();
        return service;
    }

    @Override protected View toResponse(PersonEntity entity) {
        return new View(entity.getId(), entity.displayName(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
