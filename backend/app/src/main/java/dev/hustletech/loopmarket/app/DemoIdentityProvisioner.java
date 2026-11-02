package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class DemoIdentityProvisioner implements Managed {
    private static final Logger LOG = LoggerFactory.getLogger(DemoIdentityProvisioner.class);
    private record DemoUser(String id, String username, String password, String firstName, String lastName) {}
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("01900000-0000-7000-8000-000000000201", "darth.vader", "dark-side-1977", "Darth", "Vader"),
            new DemoUser("01900000-0000-7000-8000-000000000202", "luke.skywalker", "force-1977", "Luke", "Skywalker"));
    private final URI server;
    private final String adminUser;
    private final String adminPassword;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    record Result(int status, String body) {}
    interface Transport {
        Result send(String path, String method, String token, String contentType, String body) throws Exception;
    }
    private final Transport transport;

    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json) {
        this(issuer, adminUser, adminPassword, json, null);
    }

    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json, Transport transport) {
        URI uri = URI.create(issuer);
        if (!"http".equals(uri.getScheme()) || !"identity".equals(uri.getHost())
                || !"/realms/loopmarket-dev".equals(uri.getPath())) {
            throw new IllegalArgumentException("Demo identity provisioning is restricted to the local development realm");
        }
        if (adminUser.isBlank() || adminPassword.isBlank()) throw new IllegalArgumentException("Development identity administrator is required");
        this.server = URI.create(uri.getScheme() + "://" + uri.getAuthority());
        this.adminUser = adminUser; this.adminPassword = adminPassword; this.json = json;
        this.transport = transport == null ? this::sendHttp : transport;
    }

    @Override public void start() {
        executor.scheduleWithFixedDelay(this::reconcileSafely, 0, 60, TimeUnit.SECONDS);
    }

    @Override public void stop() { executor.shutdownNow(); }

    // The local Empire returns its demo characters without resetting an existing realm.
    private void reconcileSafely() {
        try { reconcile(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        catch (Exception exception) { LOG.warn("Could not reconcile development identities: {}", exception.getClass().getSimpleName()); }
    }

    void reconcile() throws Exception {
        String form = "grant_type=password&client_id=admin-cli&username=" + encode(adminUser) + "&password=" + encode(adminPassword);
        Result auth = transport.send("/realms/master/protocol/openid-connect/token", "POST", null,
                "application/x-www-form-urlencoded", form);
        if (auth.status() != 200) throw new IllegalStateException("Identity administration is unavailable");
        String token = json.readTree(auth.body()).path("access_token").asText();
        if (token.isBlank()) throw new IllegalStateException("Identity administration returned no token");
        for (DemoUser user : USERS) ensureUser(token, user);
    }

    private void ensureUser(String token, DemoUser user) throws Exception {
        String path = "/admin/realms/loopmarket-dev/users/" + user.id();
        Result result = transport.send(path, "GET", token, null, null);
        if (result.status() == 404) {
            String body = json.writeValueAsString(Map.of("id", user.id(), "username", user.username(), "enabled", true,
                    "firstName", user.firstName(), "lastName", user.lastName(), "email", user.username() + "@example.invalid", "emailVerified", true));
            Result created = transport.send("/admin/realms/loopmarket-dev/users", "POST", token, "application/json", body);
            if (created.status() != 201) throw new IllegalStateException("Could not create a development identity");
            setPassword(token, path, user.password());
        } else if (result.status() == 200) {
            JsonNode existing = json.readTree(result.body());
            if (!user.username().equals(existing.path("username").asText())) throw new IllegalStateException("Development identity has an unexpected username");
            if (!existing.path("enabled").asBoolean() || !existing.path("emailVerified").asBoolean()
                    || !(user.username() + "@example.invalid").equals(existing.path("email").asText())) {
                String body = json.writeValueAsString(Map.of("enabled", true, "email", user.username() + "@example.invalid", "emailVerified", true));
                Result updated = transport.send(path, "PUT", token, "application/json", body);
                if (updated.status() != 204) throw new IllegalStateException("Could not enable a development identity");
            }
            setPassword(token, path, user.password());
        } else throw new IllegalStateException("Could not inspect a development identity");
    }

    private void setPassword(String token, String path, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("type", "password", "value", password, "temporary", false));
        if (transport.send(path + "/reset-password", "PUT", token, "application/json", body).status() != 204) {
            throw new IllegalStateException("Could not set a development password");
        }
    }

    private Result sendHttp(String path, String method, String token, String contentType, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(server.resolve(path)).timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (contentType != null) request.header("Content-Type", contentType);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body());
    }
    private static String encode(String input) { return URLEncoder.encode(input, StandardCharsets.UTF_8); }
}
