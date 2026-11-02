package dev.hustletech.loopmarket.app;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

// Normalize errors returned directly by resources and authentication filters, while keeping their headers.
public final class ProblemResponseFilter implements ContainerResponseFilter {
    @Override public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400) return;
        if (status == 401 && !response.getHeaders().containsKey(HttpHeaders.WWW_AUTHENTICATE)) {
            response.getHeaders().putSingle(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (!(response.getEntity() instanceof ProblemResponses.Problem)) {
            response.setEntity(ProblemResponses.body(status, null), null, MediaType.valueOf(ProblemResponses.MEDIA_TYPE));
        }
    }
}
