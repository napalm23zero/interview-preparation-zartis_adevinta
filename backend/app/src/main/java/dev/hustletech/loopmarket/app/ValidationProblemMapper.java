package dev.hustletech.loopmarket.app;

import io.dropwizard.jersey.validation.JerseyViolationException;
import jakarta.annotation.Priority;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

// Input validation is a client error; invalid return values remain a server failure.
@Priority(1)
public final class ValidationProblemMapper implements ExceptionMapper<JerseyViolationException> {
    @Override public Response toResponse(JerseyViolationException exception) {
        boolean returnValue = exception.getConstraintViolations().stream().anyMatch(violation -> {
            for (var node : violation.getPropertyPath()) if (node.getKind() == ElementKind.RETURN_VALUE) return true;
            return false;
        });
        if (returnValue) return ProblemResponses.response(500, null);
        var errors = exception.getConstraintViolations().stream().map(violation -> {
            String field = "request";
            for (var node : violation.getPropertyPath()) {
                if ((node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.PARAMETER) && node.getName() != null) field = node.getName();
            }
            return new ProblemResponses.FieldError(field, violation.getMessage());
        }).distinct().sorted(java.util.Comparator.comparing(ProblemResponses.FieldError::field)
                .thenComparing(ProblemResponses.FieldError::message)).toList();
        return ProblemResponses.validation(errors);
    }
}
