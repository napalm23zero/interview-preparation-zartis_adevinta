package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Map;

// Converts expected uniqueness and foreign-key conflicts into a stable HTTP 409 response.
public final class DatabaseConstraintMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override public Response toResponse(ConstraintViolationException exception) {
        // Keep schema details out of client responses; database errors need not become Sith gossip.
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", "The request conflicts with an existing record or reference"))
                .build();
    }
}
