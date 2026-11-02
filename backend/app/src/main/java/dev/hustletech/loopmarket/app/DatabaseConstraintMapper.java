package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.hibernate.exception.ConstraintViolationException;

// Converts expected uniqueness and foreign-key conflicts into a stable HTTP 409 response.
public final class DatabaseConstraintMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override public Response toResponse(ConstraintViolationException exception) {
        // Keep schema details out of client responses; database errors need not become Sith gossip.
        return ProblemResponses.response(409, "The request conflicts with an existing record or reference");
    }
}
