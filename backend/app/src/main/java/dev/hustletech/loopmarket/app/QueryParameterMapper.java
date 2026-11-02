package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.glassfish.jersey.server.ParamException;

// Invalid query values are client errors; the Empire keeps 404 for missing routes and resources.
public final class QueryParameterMapper implements ExceptionMapper<ParamException.QueryParamException> {
    @Override public Response toResponse(ParamException.QueryParamException exception) {
        return ProblemResponses.response(400, "Invalid query parameter: " + exception.getParameterName());
    }
}
