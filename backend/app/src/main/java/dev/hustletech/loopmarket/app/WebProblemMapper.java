package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

// Preserve explicitly authored client errors; unexpected server details remain behind the blast doors.
public final class WebProblemMapper implements ExceptionMapper<WebApplicationException> {
    @Override public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        String detail = exception.getClass() == ClientErrorException.class && status < 500 ? exception.getMessage() : null;
        return Response.fromResponse(exception.getResponse()).type(ProblemResponses.MEDIA_TYPE)
                .entity(ProblemResponses.body(status, detail)).build();
    }
}
