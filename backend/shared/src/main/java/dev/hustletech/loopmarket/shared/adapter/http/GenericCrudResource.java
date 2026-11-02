package dev.hustletech.loopmarket.shared.adapter.http;

import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.List;
import java.util.UUID;

// Common REST endpoints handle HTTP mechanics; each resource supplies policy and its safe response view.
public abstract class GenericCrudResource<E extends GenericEntity, C, U, R> {
    public enum Action { CREATE, READ, LIST, UPDATE, DELETE }

    // Enforce permissions and ownership here; even Sith need boundaries.
    protected abstract GenericCrudService<E, C, U> authorizedService(SecurityContext security, Action action, UUID id);
    protected abstract R toResponse(E entity);

    private GenericCrudService<E, C, U> service(SecurityContext security, Action action, UUID id) {
        // Reject missing identity before resource-specific policy runs; even a Sith needs a badge.
        if (security == null || security.getUserPrincipal() == null) throw new NotAuthorizedException("Bearer");
        return authorizedService(security, action, id);
    }

    @POST @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response create(@Context SecurityContext security, @Context UriInfo uri, @Context HttpHeaders headers, @Valid @NotNull C input) {
        E entity = service(security, Action.CREATE, null).create(input);
        return Response.created(uri.getAbsolutePathBuilder().path(entity.getId().toString()).build())
                .entity(toResponse(entity)).build();
    }

    @GET @Path("/{id}") @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response find(@Context SecurityContext security, @PathParam("id") UUID id) {
        return service(security, Action.READ, id).find(id).fold(
                entity -> Response.ok(toResponse(entity)).build(), () -> Response.status(404).build());
    }

    @GET @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Page<R> list(@Context SecurityContext security, @QueryParam("page") @DefaultValue("1") int page,
                        @QueryParam("size") @DefaultValue("10") int size) {
        if (page < 1 || size < 1) throw new BadRequestException("Page and size must be positive");
        size = Math.min(size, 50);
        // Widen before multiplying; integer overflow makes a poor navigation system, even for a Sith.
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new BadRequestException("Page is out of range");
        GenericCrudService<E, C, U> service = service(security, Action.LIST, null);
        long total = service.count();
        if (page > 1 && offset >= total) throw new BadRequestException("Page is out of range");
        List<R> content = service.list((int) offset, size).stream().map(this::toResponse).toList();
        return new Page<>(content, page, size, total);
    }

    @PUT @Path("/{id}") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON) @UnitOfWork
    public Response update(@Context SecurityContext security, @PathParam("id") UUID id, @Valid @NotNull U input) {
        return service(security, Action.UPDATE, id).update(id, input).fold(
                entity -> Response.ok(toResponse(entity)).build(), () -> Response.status(404).build());
    }

    @DELETE @Path("/{id}") @UnitOfWork
    public Response delete(@Context SecurityContext security, @PathParam("id") UUID id) {
        return service(security, Action.DELETE, id).delete(id).fold(
                entity -> Response.noContent().build(), () -> Response.status(404).build());
    }

    public record Page<T>(List<T> content, int page, int size, long total) {}
}
