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

class HelloApiTest {
    @RegisterExtension
    static final DropwizardAppExtension<LoopMarketConfiguration> APP =
            new DropwizardAppExtension<>(LoopMarketApplication.class, "src/test/resources/test-config.yml");

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesGreetingAndSwaggerResources() throws Exception {
        HttpResponse<String> hello = get("/api/hello");
        assertEquals(200, hello.statusCode());
        assertTrue(hello.body().contains("Hello, World!"));
        HttpResponse<String> spec = get("/api/spec/openapi.yaml");
        assertEquals(200, spec.statusCode());
        assertTrue(spec.body().contains("/hello:"));
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

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP.getLocalPort() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
