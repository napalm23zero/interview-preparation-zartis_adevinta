package dev.hustletech.loopmarket.app;

import dev.hustletech.loopmarket.identity.application.SessionService;
import dev.hustletech.loopmarket.identity.application.SessionStore;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import javax.sql.DataSource;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Priority(Priorities.AUTHENTICATION)
public final class SessionFilter implements ContainerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(SessionFilter.class);
    private final SessionService sessions;
    private final DataSource database;

    SessionFilter(SessionService sessions, DataSource database) { this.sessions = sessions; this.database = database; }

    @Override public void filter(ContainerRequestContext request) {
        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null) return;
        if (!authorization.startsWith("Bearer ")) { unauthorized(request); return; }
        try {
            SessionStore.Session session = sessions.authenticate(authorization.substring(7)).orElse(null);
            UUID id = session == null ? null : session.userId();
            String role = session == null ? null : activeRole(id, session.accountVersion());
            if (role == null) { unauthorized(request); return; }
            SecurityContext previous = request.getSecurityContext();
            request.setSecurityContext(new SecurityContext() {
                @Override public Principal getUserPrincipal() { return new SessionPrincipal(id, role); }
                @Override public boolean isUserInRole(String requestedRole) { return role.equals(requestedRole); }
                @Override public boolean isSecure() { return previous != null && previous.isSecure(); }
                @Override public String getAuthenticationScheme() { return "Bearer"; }
            });
        } catch (RuntimeException | java.sql.SQLException exception) {
            LOG.warn("Session validation dependency failed: {}", exception.getClass().getSimpleName());
            request.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
        }
    }

    private String activeRole(UUID id, long version) throws java.sql.SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT u.account_role FROM users u JOIN persons p ON p.id = u.person_id AND p.deleted_at IS NULL WHERE u.id = UUID_TO_BIN(?) AND u.auth_version = ? AND u.deleted_at IS NULL")) {
            statement.setString(1, id.toString());
            statement.setLong(2, version);
            try (var result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        }
    }

    private static void unauthorized(ContainerRequestContext request) {
        request.abortWith(Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build());
    }
}
