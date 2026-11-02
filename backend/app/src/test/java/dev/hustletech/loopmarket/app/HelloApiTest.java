package dev.hustletech.loopmarket.app;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Starts the real Dropwizard application and checks the greeting, API docs, Swagger assets, and health endpoint.
class HelloApiTest {
    @RegisterExtension
    static final DropwizardAppExtension<LoopMarketConfiguration> APP =
            new DropwizardAppExtension<>(LoopMarketApplication.class, "src/test/resources/test-config.yml");

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesGreetingAndSwaggerResources() throws Exception {
        // Exercise mounted assets over HTTP so a green unit test cannot hide a missing route.
        HttpResponse<String> hello = get("/api/hello");
        assertEquals(200, hello.statusCode());
        assertTrue(hello.body().contains("Hello, World!"));
        HttpResponse<String> spec = get("/api/spec/openapi.yaml");
        assertEquals(200, spec.statusCode());
        assertTrue(spec.body().contains("/hello"));
        var openapi = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).readTree(spec.body());
        assertEquals("#/components/schemas/PublicListingPage", openapi.path("paths").path("/catalog").path("get")
                .path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText());
        assertEquals("string", openapi.path("components").path("schemas").path("PublicListing").path("properties").path("sellerDisplayName").path("type").asText());
        HttpResponse<String> ui = get("/api/docs/");
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("../spec/openapi.yaml"));
        assertEquals(200, get("/api/swagger-ui/swagger-ui-bundle.js").statusCode());
        assertEquals(200, get("/api/swagger-ui/swagger-ui.css").statusCode());
        HttpRequest healthRequest = HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + APP.getAdminPort() + "/healthcheck")).GET().build();
        HttpResponse<String> health = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(APP.getObjectMapper().readTree(health.body()).path("database").path("healthy").asBoolean());
    }

    @Test
    void newProtectedRoutesRejectRequestsWithoutSessions() throws Exception {
        var unauthorized = get("/api/users/me");
        assertEquals(401, unauthorized.statusCode());
        assertEquals("Bearer", unauthorized.headers().firstValue("WWW-Authenticate").orElse(""));
        assertTrue(unauthorized.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals(401, APP.getObjectMapper().readTree(unauthorized.body()).path("status").asInt());
        assertEquals(401, get("/api/users/me/listings").statusCode());
        String id = java.util.UUID.randomUUID().toString();
        assertEquals(401, get("/api/users/me/listings/" + id).statusCode());
        assertEquals(401, get("/api/users/me/listings/" + id + "/interests").statusCode());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP.getLocalPort()
                + "/api/generations/" + id + "/execute")).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void publicCatalogRoutesAreAvailableWithoutSessions() throws Exception {
        HttpResponse<String> catalog = get("/api/catalog");
        assertEquals(200, catalog.statusCode());
        assertTrue(APP.getObjectMapper().readTree(catalog.body()).path("content").isArray());
        var invalid = get("/api/catalog?page=0");
        assertEquals(400, invalid.statusCode());
        assertTrue(invalid.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals(400, APP.getObjectMapper().readTree(invalid.body()).path("status").asInt());
        for (String query : new String[]{"page=abc", "size=1.5", "minPrice=invalid", "maxPrice=NaN", "minPrice=1e1000000", "maxPrice=0.001", "minPrice=10000000000"}) {
            var invalidType = get("/api/catalog?" + query);
            assertEquals(400, invalidType.statusCode(), query + ": " + invalidType.body());
            assertTrue(invalidType.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        }
        assertEquals(200, get("/api/catalog?minPrice=0e-1000000").statusCode());
        String id = java.util.UUID.randomUUID().toString();
        var missing = get("/api/catalog/" + id);
        assertEquals(404, missing.statusCode());
        assertEquals(404, APP.getObjectMapper().readTree(missing.body()).path("status").asInt());
        assertEquals(404, get("/api/catalog/" + id + "/photo").statusCode());
    }

    @Test
    void invalidRequestBodyUsesProblemDetails() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP.getLocalPort() + "/api/persons"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"\"}")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        var problem = APP.getObjectMapper().readTree(response.body());
        assertEquals(400, problem.path("status").asInt());
        assertEquals("displayName", problem.path("errors").get(0).path("field").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP.getLocalPort() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
