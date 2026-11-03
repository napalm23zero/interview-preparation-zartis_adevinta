package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

// The seeded cantina stays useful without borrowing data from another galaxy.
@EnabledIfEnvironmentVariable(named = "LOOPMARKET_DEMO_TEST_URL", matches = ".+")
class DemoCatalogIntegrationTest {
    @Test void sampleCatalogPhotosAndSellerInterestAreUsable() throws Exception {
        String base = System.getenv("LOOPMARKET_DEMO_TEST_URL");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        ObjectMapper json = new ObjectMapper();
        for (int index = 301; index <= 306; index++) {
            String id = "01900000-0000-7000-8000-%012d".formatted(index);
            var listing = http.send(HttpRequest.newBuilder(URI.create(base + "/catalog/" + id)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listing.statusCode(), id);
            assertEquals(id, json.readTree(listing.body()).path("id").asText());
            var photo = http.send(HttpRequest.newBuilder(URI.create(base + "/catalog/" + id + "/photo")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, photo.statusCode(), id);
            assertTrue(photo.headers().firstValue("Content-Type").orElse("").startsWith("image/png"));
            assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(photo.body())));
        }
        var login = http.send(HttpRequest.newBuilder(URI.create(base + "/sessions")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"luke.skywalker\",\"password\":\"force-1977\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        String token = json.readTree(login.body()).path("accessToken").asText();
        var interests = http.send(HttpRequest.newBuilder(URI.create(base + "/users/me/listings/01900000-0000-7000-8000-000000000301/interests"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, interests.statusCode(), interests.body());
        assertTrue(interests.body().contains("Darth Vader"));
        var draft = http.send(HttpRequest.newBuilder(URI.create(base + "/users/me/listings/01900000-0000-7000-8000-000000000307"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, draft.statusCode(), draft.body());
        assertEquals("DRAFT", json.readTree(draft.body()).path("status").asText());
        http.send(HttpRequest.newBuilder(URI.create(base + "/sessions")).header("Authorization", "Bearer " + token).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }
}
