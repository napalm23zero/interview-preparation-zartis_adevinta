package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hustletech.loopmarket.identity.application.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/sessions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class SessionResource {
    private static final Logger LOG = LoggerFactory.getLogger(SessionResource.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private final SessionService sessions;
    private final DataSource database;
    private final String issuer;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    SessionResource(SessionService sessions, DataSource database, String issuer, ObjectMapper json) {
        this.sessions = sessions; this.database = database; this.issuer = issuer; this.json = json;
    }

    public record Login(@NotBlank String username, @NotBlank String password) {}
    public record LoginResult(String accessToken, long expiresIn) {}

    // The provider verifies credentials before a session joins the Empire.
    @POST public Response login(@Valid @jakarta.validation.constraints.NotNull Login login) {
        try { return authenticate(login); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Response.status(503).build();
        } catch (Exception exception) {
            LOG.warn("Authentication dependency failed: {}", exception.getClass().getSimpleName());
            return Response.status(503).build();
        }
    }

    private Response authenticate(Login login) throws Exception {
        String form = "grant_type=password&scope=openid&client_id=loopmarket-dev&username=" + encode(login.username()) + "&password=" + encode(login.password());
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> token = http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (token.statusCode() == 400 || token.statusCode() == 401) return Response.status(401).build();
        if (token.statusCode() != 200) return Response.status(503).build();
        JsonNode tokenJson = json.readTree(token.body());
        String accessToken = tokenJson.path("access_token").asText();
        if (accessToken.isBlank()) return Response.status(503).build();
        HttpRequest userInfoRequest = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/userinfo"))
                .timeout(Duration.ofSeconds(10)).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).GET().build();
        HttpResponse<String> userInfo = http.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
        if (userInfo.statusCode() != 200) return Response.status(503).build();
        String subject = json.readTree(userInfo.body()).path("sub").asText();
        if (subject.isBlank()) return Response.status(503).build();
        AccountIdentity user = findUser(subject);
        if (user == null) return Response.status(403).build();
        return Response.ok(new LoginResult(sessions.create(user.id(), user.version(), TTL), TTL.toSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @DELETE public Response logout(@Context SecurityContext security, @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        if (security.getUserPrincipal() == null || authorization == null || !authorization.startsWith("Bearer ")) {
            return Response.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
        }
        sessions.revoke(authorization.substring(7));
        return Response.noContent().build();
    }

    private record AccountIdentity(UUID id, long version) {}

    private AccountIdentity findUser(String subject) throws Exception {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT BIN_TO_UUID(u.id), u.auth_version FROM users u JOIN persons p ON p.id = u.person_id AND p.deleted_at IS NULL WHERE u.issuer = ? AND u.external_subject = ? AND u.deleted_at IS NULL")) {
            statement.setString(1, issuer);
            statement.setString(2, subject);
            try (var result = statement.executeQuery()) { return result.next() ? new AccountIdentity(UUID.fromString(result.getString(1)), result.getLong(2)) : null; }
        }
    }

    private static String encode(String input) { return URLEncoder.encode(input, StandardCharsets.UTF_8); }
}
