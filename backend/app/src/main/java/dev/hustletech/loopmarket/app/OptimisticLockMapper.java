package dev.hustletech.loopmarket.app;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.Map;

public final class OptimisticLockMapper implements ExceptionMapper<OptimisticLockException> {
    @Override public Response toResponse(OptimisticLockException exception) {
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", "The record was changed by another request"))
                .build();
    }
}
