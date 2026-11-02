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
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

// This journey creates records only in an explicitly selected isolated stack; no rebels touch dev data.
@EnabledIfEnvironmentVariable(named = "LOOPMARKET_ISOLATED_URL", matches = ".+")
class MvpJourneyIntegrationTest {
    private final String base = System.getenv("LOOPMARKET_ISOLATED_URL");
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void sellerPublishesBuyerDiscoversAndSellerSeesInterest() throws Exception {
        HttpResponse<String> unauthorized = call("GET", "/users/me", null, null);
        assertProblem(unauthorized, 401);
        assertEquals("Bearer", unauthorized.headers().firstValue("WWW-Authenticate").orElse(""));
        String seller = login("darth.vader", "dark-side-1977");
        String buyer = login("luke.skywalker", "force-1977");
        HttpResponse<String> invalidCategory = call("POST", "/listings", seller, "{\"category\":\"SECRET_PLANS\"}");
        assertProblem(invalidCategory, 400);
        assertTrue(json.readTree(invalidCategory.body()).path("detail").asText().contains("category"));
        assertEquals("Darth Vader", json.readTree(call("GET", "/users/me", seller, null).body()).path("displayName").asText());
        String title = "Integration " + UUID.randomUUID();
        JsonNode listing = created(call("POST", "/listings", seller, json.writeValueAsString(Map.of(
                "title", title, "description", "A wooden chair in good condition", "category", "OTHER",
                "condition", "USED", "city", "Montreal", "price", 10, "currency", "CAD"))));
        String id = listing.path("id").asText();
        assertTrue(listing.path("createdAt").isTextual());
        assertTrue(listing.path("updatedAt").isTextual());
        assertTrue(listing.path("createdAt").asText().endsWith("Z"));
        java.time.Instant.parse(listing.path("createdAt").asText());
        assertEquals(404, call("GET", "/catalog/" + id, null, null).statusCode());
        assertEquals(404, call("GET", "/users/me/listings/" + id, buyer, null).statusCode());

        String invalidMode = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "UNSUPPORTED"));
        assertProblem(call("POST", "/generations", seller, invalidMode), 400);
        String generation = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "DETERMINISTIC"));
        JsonNode demo = created(call("POST", "/generations", seller, generation));
        assertEquals("deterministic", demo.path("provider").asText());
        assertEquals(title, demo.path("suggestions").path("title").asText());
        assertEquals("OTHER", demo.path("suggestions").path("category").asText());
        assertEquals(demo.path("id"), created(call("POST", "/generations", seller, generation)).path("id"));

        String localBody = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "LOCAL"));
        String localId = created(call("POST", "/generations", seller, localBody)).path("id").asText();
        HttpResponse<String> generated = call("POST", "/generations/" + localId + "/execute", seller, null);
        assertEquals(200, generated.statusCode(), generated.body());
        assertEquals("ollama", json.readTree(generated.body()).path("provider").asText());
        var repeatedGeneration = call("POST", "/generations/" + localId + "/execute", seller, null);
        assertEquals(200, repeatedGeneration.statusCode());
        assertEquals(json.readTree(generated.body()), json.readTree(repeatedGeneration.body()));

        JsonNode photo = created(call("POST", "/photos", seller,
                json.writeValueAsString(Map.of("listingId", id, "mediaType", "image/png", "byteSize", 1))));
        var bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes));
        assertEquals(200, call("PUT", "/photos/" + photo.path("id").asText() + "/content", seller,
                json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())))).statusCode());
        String publicationKey = UUID.randomUUID().toString();
        HttpRequest publication = HttpRequest.newBuilder(URI.create(base + "/listings/" + id + "/publications"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + seller)
                .header("Content-Type", "application/json").header("Idempotency-Key", publicationKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":0}")).build();
        var publishFirst = http.sendAsync(publication, HttpResponse.BodyHandlers.ofString());
        var publishSecond = http.sendAsync(publication, HttpResponse.BodyHandlers.ofString());
        var publicationResult = publishFirst.get(30, java.util.concurrent.TimeUnit.SECONDS);
        var publicationRetry = publishSecond.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(200, publicationResult.statusCode(), publicationResult.body());
        assertEquals(200, publicationRetry.statusCode(), publicationRetry.body());
        assertEquals(publicationResult.body(), publicationRetry.body());
        HttpRequest changedPublication = HttpRequest.newBuilder(URI.create(base + "/listings/" + id + "/publications"))
                .header("Authorization", "Bearer " + seller).header("Content-Type", "application/json")
                .header("Idempotency-Key", publicationKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":1}")).build();
        assertProblem(http.send(changedPublication, HttpResponse.BodyHandlers.ofString()), 409);
        HttpResponse<String> publicListing = call("GET", "/catalog/" + id, null, null);
        assertEquals(200, publicListing.statusCode(), publicListing.body());
        assertEquals("Darth Vader", json.readTree(publicListing.body()).path("sellerDisplayName").asText());
        var originalPhoto = photoBytes(id);
        assertEquals(200, originalPhoto.statusCode());
        assertArrayEquals(bytes.toByteArray(), originalPhoto.body());
        assertEquals("no-store", originalPhoto.headers().firstValue("Cache-Control").orElse(""));
        assertProblem(call("PUT", "/photos/" + photo.path("id").asText() + "/content", seller, "{\"base64\":\"YWJj\"}"), 400);
        assertArrayEquals(bytes.toByteArray(), photoBytes(id).body());
        var replacement = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", replacement));
        String replacementBody = json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(replacement.toByteArray())));
        assertEquals(404, call("PUT", "/photos/" + photo.path("id").asText() + "/content", buyer, replacementBody).statusCode());
        assertEquals(200, call("PUT", "/photos/" + photo.path("id").asText() + "/content", seller, replacementBody).statusCode());
        var updatedPhoto = photoBytes(id);
        assertEquals("image/jpeg", updatedPhoto.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(replacement.toByteArray(), updatedPhoto.body());
        assertEquals(200, call("GET", "/catalog?text=Integration", null, null).statusCode());
        assertProblem(call("GET", "/catalog?category=SECRET_PLANS", null, null), 400);
        String interestBody = json.writeValueAsString(Map.of("listingId", id));
        HttpRequest interestRequest = HttpRequest.newBuilder(URI.create(base + "/interests")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + buyer).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(interestBody)).build();
        var first = http.sendAsync(interestRequest, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(interestRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode interest = created(first.get(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(interest.path("id"), created(second.get(30, java.util.concurrent.TimeUnit.SECONDS)).path("id"));
        assertProblem(call("PUT", "/interests/" + interest.path("id").asText(), buyer,
                json.writeValueAsString(Map.of("listingId", UUID.randomUUID().toString()))), 409);
        HttpResponse<String> sellerInterests = call("GET", "/users/me/listings/" + id + "/interests", seller, null);
        assertEquals(200, sellerInterests.statusCode(), sellerInterests.body());
        assertEquals("Luke Skywalker", json.readTree(sellerInterests.body()).get(0).path("buyerDisplayName").asText());
        assertTrue(json.readTree(sellerInterests.body()).get(0).path("createdAt").asText().endsWith("Z"));
        JsonNode sellerPage = json.readTree(call("GET", "/users/me/listings?status=PUBLISHED", seller, null).body()).path("content");
        JsonNode sellerListing = null;
        for (JsonNode item : sellerPage) if (id.equals(item.path("id").asText())) sellerListing = item;
        assertNotNull(sellerListing);
        assertEquals(1, sellerListing.path("interestCount").asLong(-1));
        assertEquals(404, call("GET", "/users/me/listings/" + id + "/interests", buyer, null).statusCode());
        assertEquals(200, call("POST", "/listings/" + id + "/archives", seller, "{\"expectedVersion\":1}").statusCode());
        assertEquals(404, call("GET", "/catalog/" + id, null, null).statusCode());
        assertEquals(404, photoBytes(id).statusCode());
        assertEquals(publicationResult.body(), http.send(publication, HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(demo.path("id"), created(call("POST", "/generations", seller, generation)).path("id"));
        String afterArchive = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "DETERMINISTIC"));
        assertProblem(call("POST", "/generations", seller, afterArchive), 409);
    }

    @Test void generationRequestKeysPreserveCaseAndTrailingSpaces() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String listing = created(call("POST", "/listings", seller, "{\"title\":\"Book\"}")).path("id").asText();
        String prefix = UUID.randomUUID().toString();
        var ids = new java.util.HashSet<String>();
        for (String key : new String[]{prefix + "A", prefix + "a", prefix + "a "}) {
            String body = json.writeValueAsString(Map.of("listingId", listing, "requestKey", key,
                    "inputVersion", 0, "clientRevision", "r1", "mode", "DETERMINISTIC"));
            JsonNode attempt = created(call("POST", "/generations", seller, body));
            assertEquals(key, attempt.path("requestKey").asText());
            assertTrue(ids.add(attempt.path("id").asText()));
            assertEquals(attempt.path("id"), created(call("POST", "/generations", seller, body)).path("id"));
        }
    }

    @Test @EnabledIfEnvironmentVariable(named = "LOOPMARKET_LARGE_PHOTO", matches = "true")
    void enforcesExactConfiguredPhotoLimitThroughIngress() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String listing = created(call("POST", "/listings", seller, "{\"title\":\"Large photo\"}")).path("id").asText();
        var image = new java.awt.image.BufferedImage(2640, 2640, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(42);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        var bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image, "png", bytes));
        assertTrue(bytes.size() > 19922944 && bytes.size() < 20971520, "image bytes: " + bytes.size());
        // Reach the exact boundary with a valid PNG chunk; the pixels keep their Sith secrets.
        byte[] png = bytes.toByteArray();
        byte[] padding = new byte[20971520 - png.length - 12];
        byte[] chunkType = "npAd".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var crc = new java.util.zip.CRC32();
        crc.update(chunkType);
        crc.update(padding);
        var exact = new java.io.ByteArrayOutputStream(20971520);
        var chunks = new java.io.DataOutputStream(exact);
        chunks.write(png, 0, png.length - 12);
        chunks.writeInt(padding.length);
        chunks.write(chunkType);
        chunks.write(padding);
        chunks.writeInt((int) crc.getValue());
        chunks.write(png, png.length - 12, 12);
        byte[] exactImage = exact.toByteArray();
        assertEquals(20971520, exactImage.length);
        assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(exactImage)));
        String photo = created(call("POST", "/photos", seller, json.writeValueAsString(Map.of(
                "listingId", listing, "mediaType", "image/png", "byteSize", exactImage.length)))).path("id").asText();
        var upload = call("PUT", "/photos/" + photo + "/content", seller,
                json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(exactImage))));
        assertEquals(200, upload.statusCode(), upload.body());
        byte[] oversized = java.util.Arrays.copyOf(exactImage, exactImage.length + 1);
        assertProblem(call("PUT", "/photos/" + photo + "/content", seller,
                json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(oversized)))), 400);
        HttpRequest read = HttpRequest.newBuilder(URI.create(base + "/photos/" + photo + "/content"))
                .header("Authorization", "Bearer " + seller).GET().build();
        var stored = http.send(read, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, stored.statusCode());
        assertArrayEquals(exactImage, stored.body());
    }

    @Test void logoutRevokesOnlyTheCurrentOpaqueSession() throws Exception {
        String first = login("luke.skywalker", "force-1977");
        String second = login("luke.skywalker", "force-1977");
        assertNotEquals(first, second);
        assertEquals(200, call("GET", "/users/me", first, null).statusCode());
        assertEquals(204, call("DELETE", "/sessions", first, null).statusCode());
        HttpResponse<String> revoked = call("GET", "/users/me", first, null);
        assertProblem(revoked, 401);
        assertEquals("Bearer", revoked.headers().firstValue("WWW-Authenticate").orElse(""));
        assertEquals(200, call("GET", "/users/me", second, null).statusCode());
        assertEquals(204, call("DELETE", "/sessions", second, null).statusCode());
    }

    @Test void accountUpdateRevokesExistingSessionsWithoutGrantingAdminAccess() throws Exception {
        String admin = login("darth.vader", "dark-side-1977");
        String user = login("luke.skywalker", "force-1977");
        assertProblem(call("GET", "/users", user, null), 403);
        String userId = json.readTree(call("GET", "/users/me", user, null).body()).path("id").asText();
        HttpResponse<String> accountResponse = call("GET", "/users/" + userId, admin, null);
        assertEquals(200, accountResponse.statusCode(), accountResponse.body());
        JsonNode account = json.readTree(accountResponse.body());
        String update = json.writeValueAsString(Map.of("personId", account.path("personId").asText(),
                "issuer", account.path("issuer").asText(), "externalSubject", account.path("externalSubject").asText(),
                "role", account.path("role").asText()));
        assertEquals(200, call("PUT", "/users/" + userId, admin, update).statusCode());
        assertProblem(call("GET", "/users/me", user, null), 401);
        String renewed = login("luke.skywalker", "force-1977");
        assertEquals(200, call("GET", "/users/me", renewed, null).statusCode());
        assertProblem(call("GET", "/users", renewed, null), 403);
    }

    @Test void concurrentDraftRequestsReplayOriginalAndRejectChangedPayload() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("title", "Idempotent " + UUID.randomUUID()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/listings")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + seller).header("Content-Type", "application/json")
                .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
        var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(201, a.statusCode(), a.body());
        assertEquals(201, b.statusCode(), b.body());
        assertEquals(a.body(), b.body());
        assertEquals(a.headers().firstValue("Location"), b.headers().firstValue("Location"));
        assertEquals(base + "/listings/" + json.readTree(a.body()).path("id").asText(), a.headers().firstValue("Location").orElse(""));
        HttpRequest changed = HttpRequest.newBuilder(URI.create(base + "/listings")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + seller).header("Content-Type", "application/json")
                .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Different\"}")).build();
        assertProblem(http.send(changed, HttpResponse.BodyHandlers.ofString()), 409);
    }

    @Test void concurrentEditsRejectTheStaleVersion() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String id = created(call("POST", "/listings", seller, "{\"title\":\"Initial\"}")).path("id").asText();
        HttpRequest edit = HttpRequest.newBuilder(URI.create(base + "/listings/" + id)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + seller).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"title\":\"Edited\",\"expectedVersion\":0}")).build();
        var first = http.sendAsync(edit, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(edit, HttpResponse.BodyHandlers.ofString());
        var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
        var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(java.util.Set.of(200, 409), java.util.Set.of(a.statusCode(), b.statusCode()), a.body() + " / " + b.body());
        assertProblem(a.statusCode() == 409 ? a : b, 409);
        assertEquals(1, json.readTree(call("GET", "/users/me/listings/" + id, seller, null).body()).path("version").asLong());
    }

    @Test @EnabledIfEnvironmentVariable(named = "LOOPMARKET_GATEWAY_STUB", matches = "true")
    void repeatedExecutionReportsProcessingWithoutAnotherProviderCall() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String id = created(call("POST", "/listings", seller, "{\"title\":\"Book\"}")).path("id").asText();
        String request = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "LOCAL"));
        String attempt = created(call("POST", "/generations", seller, request)).path("id").asText();
        HttpRequest execute = HttpRequest.newBuilder(URI.create(base + "/generations/" + attempt + "/execute"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + seller)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        URI counter = URI.create(System.getenv("LOOPMARKET_GATEWAY_STUB_URL") + "/calls");
        int callsBefore = Integer.parseInt(http.send(HttpRequest.newBuilder(counter).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        var first = http.sendAsync(execute, HttpResponse.BodyHandlers.ofString());
        assertThrows(java.util.concurrent.TimeoutException.class, () -> first.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
        var second = http.send(execute, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, second.statusCode(), second.body());
        assertEquals("PROCESSING", json.readTree(second.body()).path("status").asText());
        var finished = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(200, finished.statusCode(), finished.body());
        var replay = http.send(execute, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(json.readTree(finished.body()), json.readTree(replay.body()));
        int callsAfter = Integer.parseInt(http.send(HttpRequest.newBuilder(counter).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(callsBefore + 1, callsAfter);
    }

    @Test @EnabledIfEnvironmentVariable(named = "LOOPMARKET_GATEWAY_OFFLINE", matches = "true")
    void gatewayOutageLeavesTheDraftEditableAndTheOutcomeUnknown() throws Exception {
        String seller = login("darth.vader", "dark-side-1977");
        String id = created(call("POST", "/listings", seller, "{\"title\":\"Manual item\"}")).path("id").asText();
        String request = json.writeValueAsString(Map.of("listingId", id, "requestKey", UUID.randomUUID().toString(),
                "inputVersion", 0, "clientRevision", "r1", "mode", "LOCAL"));
        String attempt = created(call("POST", "/generations", seller, request)).path("id").asText();
        HttpResponse<String> failure = call("POST", "/generations/" + attempt + "/execute", seller, null);
        assertProblem(failure, 503);
        assertEquals("GENERATION_OUTCOME_UNKNOWN", json.readTree(failure.body()).path("code").asText());
        assertEquals("UNKNOWN", json.readTree(call("GET", "/generations/" + attempt, seller, null).body()).path("status").asText());
        var repeated = call("POST", "/generations/" + attempt + "/execute", seller, null);
        assertEquals(200, repeated.statusCode());
        assertEquals("UNKNOWN", json.readTree(repeated.body()).path("status").asText());
        assertEquals(200, call("PUT", "/listings/" + id, seller, "{\"title\":\"Edited manually\",\"expectedVersion\":0}").statusCode());
    }

    @Test void archiveCommittedWhileInterestWaitsPreventsLateInterest() throws Exception {
        String jdbc = System.getenv("LOOPMARKET_ISOLATED_JDBC_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(jdbc != null && !jdbc.isBlank());
        String seller = login("darth.vader", "dark-side-1977");
        String buyer = login("luke.skywalker", "force-1977");
        JsonNode listing = created(call("POST", "/listings", seller, json.writeValueAsString(Map.of(
                "title", "Lock test " + UUID.randomUUID(), "description", "Used chair", "category", "OTHER",
                "condition", "USED", "city", "Montreal", "price", 10, "currency", "CAD"))));
        String id = listing.path("id").asText();
        JsonNode photo = created(call("POST", "/photos", seller,
                json.writeValueAsString(Map.of("listingId", id, "mediaType", "image/png", "byteSize", 1))));
        var bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes));
        assertEquals(200, call("PUT", "/photos/" + photo.path("id").asText() + "/content", seller,
                json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())))).statusCode());
        assertEquals(200, call("POST", "/listings/" + id + "/publications", seller, "{\"expectedVersion\":0}").statusCode());
        try (var connection = java.sql.DriverManager.getConnection(jdbc, System.getenv("LOOPMARKET_ISOLATED_DB_USER"), System.getenv("LOOPMARKET_ISOLATED_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM listings WHERE id=UUID_TO_BIN(?) FOR UPDATE")) {
                lock.setString(1, id);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
            }
            String body = json.writeValueAsString(Map.of("listingId", id));
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/interests")).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + buyer).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            // Hold the actual InnoDB row lock while the request approaches; the Empire loves a queue.
            assertThrows(java.util.concurrent.TimeoutException.class, () -> pending.get(1, java.util.concurrent.TimeUnit.SECONDS));
            try (var update = connection.prepareStatement("UPDATE listings SET status='ARCHIVED', version=version+1 WHERE id=UUID_TO_BIN(?)")) {
                update.setString(1, id);
                assertEquals(1, update.executeUpdate());
            }
            connection.commit();
            assertEquals(404, pending.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
        }
    }

    @Test void interestBeforeArchiveKeepsHistoricalInterest() throws Exception { verifyInterestArchiveOrder(true, false); }
    @Test void archiveBeforeInterestRejectsLateInterest() throws Exception { verifyInterestArchiveOrder(false, false); }

    @Test void deletionBeforeInterestRejectsLateInterest() throws Exception { verifyInterestArchiveOrder(false, true); }

    private void verifyInterestArchiveOrder(boolean interestFirst, boolean deleteFirst) throws Exception {
        String jdbc = System.getenv("LOOPMARKET_ISOLATED_JDBC_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(jdbc != null && !jdbc.isBlank());
        String seller = login("darth.vader", "dark-side-1977");
        String buyer = login("luke.skywalker", "force-1977");
        String id = created(call("POST", "/listings", seller, json.writeValueAsString(Map.of(
                "title", "Interest first " + UUID.randomUUID(), "description", "Used chair", "category", "OTHER",
                "condition", "USED", "city", "Montreal", "price", 10, "currency", "CAD")))).path("id").asText();
        String photo = created(call("POST", "/photos", seller,
                json.writeValueAsString(Map.of("listingId", id, "mediaType", "image/png", "byteSize", 1)))).path("id").asText();
        var bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes));
        assertEquals(200, call("PUT", "/photos/" + photo + "/content", seller,
                json.writeValueAsString(Map.of("base64", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())))).statusCode());
        assertEquals(200, call("POST", "/listings/" + id + "/publications", seller, "{\"expectedVersion\":0}").statusCode());
        try (var connection = java.sql.DriverManager.getConnection(jdbc, System.getenv("LOOPMARKET_ISOLATED_DB_USER"), System.getenv("LOOPMARKET_ISOLATED_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM listings WHERE id=UUID_TO_BIN(?) FOR UPDATE")) {
                lock.setString(1, id);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
            }
            HttpRequest interest = HttpRequest.newBuilder(URI.create(base + "/interests")).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + buyer).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("listingId", id)))).build();
            var action = HttpRequest.newBuilder(URI.create(base + "/listings/" + id + (deleteFirst ? "" : "/archives")))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + seller);
            HttpRequest archive = deleteFirst ? action.DELETE().build()
                    : action.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":1}")).build();
            var first = http.sendAsync(interestFirst ? interest : archive, HttpResponse.BodyHandlers.ofString());
            assertThrows(java.util.concurrent.TimeoutException.class, () -> first.get(1, java.util.concurrent.TimeUnit.SECONDS));
            var second = http.sendAsync(interestFirst ? archive : interest, HttpResponse.BodyHandlers.ofString());
            assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(1, java.util.concurrent.TimeUnit.SECONDS));
            connection.commit();
            assertEquals(interestFirst ? 201 : deleteFirst ? 204 : 200, first.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
            assertEquals(interestFirst ? 200 : 404, second.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode());
        }
        assertEquals(404, call("GET", "/catalog/" + id, null, null).statusCode());
        if (!deleteFirst) {
            JsonNode interests = json.readTree(call("GET", "/users/me/listings/" + id + "/interests", seller, null).body());
            assertEquals(interestFirst ? 1 : 0, interests.size());
            if (interestFirst) assertEquals("Luke Skywalker", interests.get(0).path("buyerDisplayName").asText());
        }
        assertEquals(404, call("POST", "/interests", buyer, json.writeValueAsString(Map.of("listingId", id))).statusCode());
    }

    private HttpResponse<byte[]> photoBytes(String listingId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/catalog/" + listingId + "/photo"))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode created(HttpResponse<String> response) throws Exception {
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
    private void assertProblem(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals(status, json.readTree(response.body()).path("status").asInt());
    }
    private String login(String username, String password) throws Exception {
        HttpResponse<String> response = call("POST", "/sessions", null, json.writeValueAsString(Map.of("username", username, "password", password)));
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("accessToken").asText();
    }
    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        if ("POST".equals(method) && ("/listings".equals(path) || path.endsWith("/publications"))) request.header("Idempotency-Key", UUID.randomUUID().toString());
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
