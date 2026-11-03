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

// Reconciles fixed development users with the local identity provider while the app is running.
final class DemoIdentityProvisioner implements Managed {
    private static final Logger LOG = LoggerFactory.getLogger(DemoIdentityProvisioner.class);
    private record DemoUser(String id, String username, String password, String firstName, String lastName) {}
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("01900000-0000-7000-8000-000000000201", "darth.vader", "dark-side-1977", "Darth", "Vader"),
            new DemoUser("01900000-0000-7000-8000-000000000202", "luke.skywalker", "force-1977", "Luke", "Skywalker"),
            new DemoUser("01900000-0000-7000-8000-000000000203", "leia.organa", "rebel-1977", "Leia", "Organa"),
            new DemoUser("01900000-0000-7000-8000-000000000204", "han.solo", "falcon-1977", "Han", "Solo"));
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
    interface Binder { void bind(String expectedId, String actualId, boolean created) throws Exception; }
    private final Transport transport;
    private final Binder binder;

    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json) {
        this(issuer, adminUser, adminPassword, json, null, (expected, actual, created) -> {});
    }
    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json, Transport transport) {
        this(issuer, adminUser, adminPassword, json, transport, (expected, actual, created) -> {});
    }
    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json, javax.sql.DataSource database) {
        this(issuer, adminUser, adminPassword, json, null, (expected, actual, created) -> bindAccount(database, issuer, expected, actual, created));
    }
    DemoIdentityProvisioner(String issuer, String adminUser, String adminPassword, ObjectMapper json, Transport transport, Binder binder) {
        URI uri = URI.create(issuer);
        if (!"http".equals(uri.getScheme()) || !"identity".equals(uri.getHost())
                || !"/realms/loopmarket-dev".equals(uri.getPath())) {
            throw new IllegalArgumentException("Demo identity provisioning is restricted to the local development realm");
        }
        if (adminUser.isBlank() || adminPassword.isBlank()) throw new IllegalArgumentException("Development identity administrator is required");
        this.server = URI.create(uri.getScheme() + "://" + uri.getAuthority());
        this.binder = binder;
        this.adminUser = adminUser; this.adminPassword = adminPassword; this.json = json;
        this.transport = transport == null ? this::sendHttp : transport;
    }

    @Override public void start() {
        executor.scheduleWithFixedDelay(this::reconcileSafely, 0, 60, TimeUnit.SECONDS);
    }

    @Override public void stop() { executor.shutdownNow(); }

    // Dev-only reconciliation restores known demo credentials; keep this provisioner out of production.
    private void reconcileSafely() {
        try { reconcile(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        catch (Exception exception) { LOG.warn("Could not reconcile development identities: {}", exception.getClass().getSimpleName()); }
    }

    void reconcile() throws Exception {
        // Obtain a short-lived admin token, then create or repair only the known development identities.
        String form = "grant_type=password&client_id=admin-cli&username=" + encode(adminUser) + "&password=" + encode(adminPassword);
        Result auth = transport.send("/realms/master/protocol/openid-connect/token", "POST", null,
                "application/x-www-form-urlencoded", form);
        if (auth.status() != 200) throw new IllegalStateException("Identity administration is unavailable");
        String token = json.readTree(auth.body()).path("access_token").asText();
        if (token.isBlank()) throw new IllegalStateException("Identity administration returned no token");
        for (DemoUser user : USERS) ensureUser(token, user);
    }

    private void ensureUser(String token, DemoUser user) throws Exception {
        String root = "/admin/realms/loopmarket-dev/users";
        String path = root + "/" + user.id();
        Result result = transport.send(path, "GET", token, null, null);
        JsonNode existing;
        String actualId = user.id();
        boolean created = false;
        if (result.status() == 404) {
            existing = searchDemoUser(root, token, user);
            if (existing == null) {
                String body = json.writeValueAsString(Map.of("id", user.id(), "username", user.username(), "enabled", true,
                        "firstName", user.firstName(), "lastName", user.lastName(), "email", user.username() + "@example.invalid",
                        "emailVerified", true));
                Result response = transport.send(root, "POST", token, "application/json", body);
                if (response.status() != 201) throw new IllegalStateException("Could not create a development identity");
                created = true;
                existing = searchDemoUser(root, token, user);
                if (existing == null) throw new IllegalStateException("Created development identity was not found");
            }
            actualId = existing.path("id").asText();
            java.util.UUID.fromString(actualId);
            // Bind only a newly created identity or one already linked to the known local account.
            if (!user.id().equals(actualId)) binder.bind(user.id(), actualId, created);
            path = root + "/" + actualId;
        } else if (result.status() == 200) {
            existing = json.readTree(result.body());
        } else throw new IllegalStateException("Could not inspect a development identity");
        if (!user.username().equals(existing.path("username").asText())) throw new IllegalStateException("Development identity has an unexpected username");
        if (!existing.path("enabled").asBoolean() || !existing.path("emailVerified").asBoolean()
                || !(user.username() + "@example.invalid").equals(existing.path("email").asText())) {
            var repaired = ((com.fasterxml.jackson.databind.node.ObjectNode) existing).deepCopy();
            repaired.put("enabled", true);
            repaired.put("email", user.username() + "@example.invalid");
            repaired.put("emailVerified", true);
            String body = json.writeValueAsString(repaired);
            Result updated = transport.send(path, "PUT", token, "application/json", body);
            if (updated.status() != 204) throw new IllegalStateException("Could not enable a development identity");
        }
        setPassword(token, path, user.password());
    }

    private JsonNode searchDemoUser(String root, String token, DemoUser user) throws Exception {
        Result result = transport.send(root + "?username=" + encode(user.username()) + "&exact=true&briefRepresentation=false", "GET", token, null, null);
        if (result.status() != 200) throw new IllegalStateException("Could not search development identities");
        JsonNode matches = json.readTree(result.body());
        if (!matches.isArray() || matches.size() > 1) throw new IllegalStateException("Ambiguous development identity");
        if (matches.isEmpty()) return null;
        JsonNode found = matches.get(0);
        if (!user.username().equals(found.path("username").asText())
                || !(user.username() + "@example.invalid").equals(found.path("email").asText()))
            throw new IllegalStateException("Existing identity is not a verified demo user");
        return found;
    }

    private static void bindAccount(javax.sql.DataSource database, String issuer, String expectedId, String actualId, boolean created) throws Exception {
        String accountId = expectedId.substring(0, expectedId.length() - 3)
                + "%03d".formatted(Integer.parseInt(expectedId.substring(expectedId.length() - 3)) - 100);
        try (var connection = database.getConnection()) {
            if (created) {
                try (var update = connection.prepareStatement("UPDATE users SET external_subject = ?, auth_version = auth_version + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?) AND issuer = ? AND external_subject = ? AND deleted_at IS NULL")) {
                    update.setString(1, actualId); update.setString(2, accountId); update.setString(3, issuer); update.setString(4, expectedId);
                    if (update.executeUpdate() == 1) return;
                }
            }
            try (var check = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE id = UUID_TO_BIN(?) AND issuer = ? AND external_subject = ? AND deleted_at IS NULL")) {
                check.setString(1, accountId); check.setString(2, issuer); check.setString(3, actualId);
                try (var rows = check.executeQuery()) { if (!rows.next() || rows.getLong(1) != 1) throw new IllegalStateException("Development account binding changed"); }
            }
        }
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
