package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "LOOPMARKET_E2E", matches = "true")
class SessionCrudIntegrationTest {
    private static final String BASE = System.getenv().getOrDefault("LOOPMARKET_E2E_URL", "http://backend:8080/api");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test void sessionsEnforceRolesOwnershipAndRevocation() throws Exception {
        String admin = login("darth.vader", "dark-side-1977");
        String user = login("luke.skywalker", "force-1977");
        assertEquals(401, call("GET", "/persons", null, null).statusCode());
        assertEquals(403, call("GET", "/persons", user, null).statusCode());
        assertEquals(200, call("GET", "/persons", admin, null).statusCode());
        String lukeAccount = json.writeValueAsString(java.util.Map.of(
                "personId", "01900000-0000-7000-8000-000000000002",
                "issuer", "http://identity:" + System.getenv("IDENTITY_PORT") + "/realms/loopmarket-dev",
                "externalSubject", "01900000-0000-7000-8000-000000000202", "role", "USER"));
        assertEquals(200, call("PUT", "/users/01900000-0000-7000-8000-000000000102", admin, lukeAccount).statusCode());
        assertEquals(401, call("GET", "/listings", user, null).statusCode());
        user = login("luke.skywalker", "force-1977");
        HttpResponse<String> person = call("POST", "/persons", admin, "{\"displayName\":\"Integration Test\"}");
        assertEquals(201, person.statusCode(), person.body());
        String personId = json.readTree(person.body()).path("id").asText();
        assertEquals(200, call("GET", "/persons/" + personId, admin, null).statusCode());
        assertEquals(200, call("PUT", "/persons/" + personId, admin, "{\"displayName\":\"Integration Updated\"}").statusCode());
        String accountBody = json.writeValueAsString(java.util.Map.of("personId", personId, "issuer", "https://example.invalid",
                "externalSubject", UUID.randomUUID().toString(), "role", "USER"));
        HttpResponse<String> account = call("POST", "/users", admin, accountBody);
        assertEquals(201, account.statusCode(), account.body());
        String accountId = json.readTree(account.body()).path("id").asText();
        assertListed("/users", admin, accountId, true);
        assertEquals(200, call("GET", "/users/" + accountId, admin, null).statusCode());
        assertEquals(200, call("PUT", "/users/" + accountId, admin, accountBody).statusCode());
        assertEquals(403, call("GET", "/users/" + accountId, user, null).statusCode());
        assertEquals(204, call("DELETE", "/users/" + accountId, admin, null).statusCode());
        assertEquals(204, call("DELETE", "/persons/" + personId, admin, null).statusCode());

        HttpResponse<String> created = call("POST", "/listings", admin,
                "{\"title\":\"Integration " + UUID.randomUUID() + "\"}");
        assertEquals(201, created.statusCode(), created.body());
        JsonNode listing = json.readTree(created.body());
        String id = listing.path("id").asText();
        assertEquals(7, UUID.fromString(id).version());
        assertEquals("DRAFT", listing.path("status").asText());
        assertListed("/listings", admin, id, true);
        assertListed("/listings", user, id, false);
        assertEquals(404, call("GET", "/listings/" + id, user, null).statusCode());
        assertEquals(404, call("PUT", "/listings/" + id, user,
                "{\"title\":\"Forbidden\",\"expectedVersion\":0}").statusCode());
        assertEquals(200, call("PUT", "/listings/" + id, admin,
                "{\"title\":\"Updated\",\"description\":\"A useful item\",\"category\":\"OTHER\",\"condition\":\"USED\",\"city\":\"Montreal\",\"price\":10.00,\"currency\":\"CAD\",\"expectedVersion\":0}").statusCode());
        assertEquals(409, call("PUT", "/listings/" + id, admin,
                "{\"title\":\"Stale\",\"expectedVersion\":0}").statusCode());
        String generationBody = json.writeValueAsString(java.util.Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 1, "clientRevision", "e2e-1", "mode", "TEXT"));
        HttpResponse<String> generation = call("POST", "/generations", admin, generationBody);
        assertEquals(201, generation.statusCode(), generation.body());
        String generationId = json.readTree(generation.body()).path("id").asText();
        assertListed("/generations", admin, generationId, true);
        assertListed("/generations", user, generationId, false);
        assertEquals(200, call("GET", "/generations/" + generationId, admin, null).statusCode());
        assertEquals(404, call("GET", "/generations/" + generationId, user, null).statusCode());
        assertEquals(200, call("PUT", "/generations/" + generationId, admin, "{\"status\":\"FAILED\"}").statusCode());
        assertEquals(204, call("DELETE", "/generations/" + generationId, admin, null).statusCode());
        assertEquals(409, call("POST", "/listings/" + id + "/publications", admin, "{\"expectedVersion\":1}").statusCode());
        HttpResponse<String> photo = call("POST", "/photos", admin,
                "{\"listingId\":\"" + id + "\",\"mediaType\":\"image/png\",\"byteSize\":1}");
        assertEquals(201, photo.statusCode(), photo.body());
        String photoId = json.readTree(photo.body()).path("id").asText();
        assertListed("/photos", admin, photoId, true);
        assertListed("/photos", user, photoId, false);
        assertEquals(200, call("GET", "/photos/" + photoId, admin, null).statusCode());
        assertEquals(404, call("GET", "/photos/" + photoId, user, null).statusCode());
        assertEquals(200, call("PUT", "/photos/" + photoId, admin,
                "{\"listingId\":\"" + id + "\",\"mediaType\":\"image/png\",\"byteSize\":1}").statusCode());
        assertEquals(204, call("DELETE", "/photos/" + photoId, admin, null).statusCode());
        HttpResponse<String> replacement = call("POST", "/photos", admin,
                "{\"listingId\":\"" + id + "\",\"mediaType\":\"image/png\",\"byteSize\":1}");
        assertEquals(201, replacement.statusCode(), replacement.body());
        photoId = json.readTree(replacement.body()).path("id").asText();
        java.io.ByteArrayOutputStream imageBytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes));
        String image = java.util.Base64.getEncoder().encodeToString(imageBytes.toByteArray());
        assertEquals(200, call("PUT", "/photos/" + photoId + "/content", admin,
                json.writeValueAsString(java.util.Map.of("base64", image))).statusCode());
        assertEquals(200, call("GET", "/photos/" + photoId + "/content", admin, null).statusCode());
        assertEquals(404, call("GET", "/photos/" + photoId + "/content", user, null).statusCode());
        assertEquals(200, call("POST", "/listings/" + id + "/publications", admin, "{\"expectedVersion\":1}").statusCode());
        HttpResponse<String> interest = call("POST", "/interests", user, "{\"listingId\":\"" + id + "\"}");
        assertEquals(201, interest.statusCode(), interest.body());
        String interestId = json.readTree(interest.body()).path("id").asText();
        assertListed("/interests", user, interestId, true);
        assertListed("/interests", admin, interestId, false);
        assertEquals(200, call("GET", "/interests/" + interestId, user, null).statusCode());
        assertEquals(404, call("GET", "/interests/" + interestId, admin, null).statusCode());
        assertEquals(200, call("PUT", "/interests/" + interestId, user, "{\"listingId\":\"" + id + "\"}").statusCode());
        HttpResponse<String> duplicate = call("POST", "/interests", user, "{\"listingId\":\"" + id + "\"}");
        assertEquals(interestId, json.readTree(duplicate.body()).path("id").asText());
        assertEquals(403, call("POST", "/interests", admin, "{\"listingId\":\"" + id + "\"}").statusCode());
        assertEquals(204, call("DELETE", "/interests/" + interestId, user, null).statusCode());
        assertEquals(200, call("POST", "/listings/" + id + "/archives", admin, "{\"expectedVersion\":2}").statusCode());
        assertEquals(204, call("DELETE", "/listings/" + id, admin, null).statusCode());
        assertEquals(404, call("GET", "/listings/" + id, admin, null).statusCode());
        assertEquals(204, call("DELETE", "/sessions", admin, null).statusCode());
        assertEquals(401, call("GET", "/persons", admin, null).statusCode());
        assertEquals(204, call("DELETE", "/sessions", user, null).statusCode());
    }

    private String login(String username, String password) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("username", username, "password", password));
        HttpResponse<String> result = call("POST", "/sessions", null, body);
        assertEquals(200, result.statusCode(), result.body());
        String token = json.readTree(result.body()).path("accessToken").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private void assertListed(String path, String token, String id, boolean expected) throws Exception {
        HttpResponse<String> response = call("GET", path + "?size=50", token, null);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode content = json.readTree(response.body()).path("content");
        assertTrue(content.isArray());
        boolean found = false;
        for (JsonNode item : content) if (id.equals(item.path("id").asText())) found = true;
        assertEquals(expected, found, path + " must respect the caller's scope");
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
