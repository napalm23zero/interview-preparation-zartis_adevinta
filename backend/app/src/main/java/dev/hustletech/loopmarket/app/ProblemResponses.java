package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.core.Response;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// One error envelope keeps API clients informed without revealing the Empire's internals.
public final class ProblemResponses {
    public static final String MEDIA_TYPE = "application/problem+json";
    public record FieldError(String field, String message) {}
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Problem(String type, String title, int status, String detail, String code, List<FieldError> errors) {}
    private ProblemResponses() {}

    public static Problem body(int status, String detail) {
        Response.Status known = Response.Status.fromStatusCode(status);
        String title = known == null ? (status == 422 ? "Unprocessable Content" : "HTTP error") : known.getReasonPhrase();
        return new Problem("about:blank", title, status, detail == null || detail.isBlank() ? title : detail, null, List.of());
    }

    public static Response response(int status, String detail) {
        return Response.status(status).type(MEDIA_TYPE).entity(body(status, detail)).build();
    }

    public static Response gatewayUnavailable() {
        return Response.status(503).type(MEDIA_TYPE).entity(new Problem("about:blank", "Service Unavailable", 503,
                "Generation is unavailable; the attempt outcome is unknown", "GENERATION_OUTCOME_UNKNOWN", List.of())).build();
    }

    public static Response validation(List<FieldError> errors) {
        return Response.status(400).type(MEDIA_TYPE)
                .entity(new Problem("about:blank", "Bad Request", 400, "Request validation failed", null, errors)).build();
    }
}
