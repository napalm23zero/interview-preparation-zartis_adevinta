package dev.hustletech.loopmarket.app.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hustletech.loopmarket.app.listing.ListingCategories;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// The gateway is an untrusted boundary too; Sith titles do not grant its JSON a free pass.
final class GatewayClient {
    record Result(String provider, String model, Map<String, String> suggestions) {}
    private static final int MAX_RESPONSE = 65536;
    private final URI endpoint;
    private final ObjectMapper json;
    private final HttpClient http;

    GatewayClient(String baseUrl, ObjectMapper json) {
        this.endpoint = URI.create(baseUrl).resolve("/generate");
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    Result generate(Map<String, String> facts) throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>(facts);
        String image = fields.remove("image");
        String model = fields.remove("model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facts", fields);
        if (image != null) payload.put("image", image);
        if (model != null) payload.put("model", model);
        byte[] body = json.writeValueAsBytes(payload);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(100))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var future = http.sendAsync(request, info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = future.get(100, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IOException("Gateway returned HTTP " + response.statusCode());
            return validate(json.readTree(response.body()));
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IOException("Gateway timed out", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throw new IOException("Gateway request failed", exception.getCause());
        }
    }

    JsonNode models() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/models")).timeout(Duration.ofSeconds(8)).GET().build();
        var response = http.send(request, info -> new LimitedBody());
        if (response.statusCode() != 200) throw new IOException("Gateway unavailable");
        JsonNode result = json.readTree(response.body());
        if (!result.path("models").isArray()) throw new IOException("Invalid model list");
        return result;
    }

    // Bound the stream before buffering, so an overeager gateway cannot swallow the Death Star's memory.
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int size;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE - size) {
                    subscription.cancel();
                    delegate.onError(new IOException("Gateway response too large"));
                    return;
                }
                size += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
    }

    private static Result validate(JsonNode root) throws IOException {
        if (root == null || !root.isObject() || !root.path("provider").isTextual()) throw new IOException("Invalid gateway response");
        var rootFields = root.fieldNames();
        while (rootFields.hasNext()) if (!Set.of("provider", "model", "suggestions").contains(rootFields.next())) throw new IOException("Unsupported gateway field");
        String provider = root.path("provider").asText();
        if (!Set.of("deterministic", "ollama").contains(provider)) throw new IOException("Unknown gateway provider");
        JsonNode modelNode = root.path("model");
        String model = modelNode.isTextual() ? modelNode.asText() : null;
        if ((model != null && (model.isBlank() || model.length() > 120)) || (provider.equals("ollama") && model == null)) {
            throw new IOException("Invalid gateway model");
        }
        JsonNode fields = root.path("suggestions");
        if (!fields.isObject() || fields.isEmpty()) throw new IOException("Invalid gateway suggestions");
        Map<String, String> suggestions = new LinkedHashMap<>();
        var names = fields.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            int limit = switch (name) { case "title" -> 120; case "description" -> 3000; case "category" -> 80; case "condition" -> 20; case "price" -> 13; default -> 0; };
            JsonNode value = fields.get(name);
            if (limit == 0 || !value.isTextual()) throw new IOException("Unsupported suggestion field");
            String text = value.asText();
            if (text.isBlank() || text.length() > limit || (name.equals("category") && !ListingCategories.contains(text)) || (name.equals("condition") && !Set.of("NEW", "LIKE_NEW", "GOOD", "FAIR", "USED", "BAD", "VERY_BAD").contains(text)) || (name.equals("price") && !text.matches("(?:0|[1-9][0-9]{0,9})(?:\\.[0-9]{1,2})?")) || text.indexOf('<') >= 0 || text.indexOf('>') >= 0
                    || text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')) {
                throw new IOException("Invalid suggestion text");
            }
            suggestions.put(name, text);
        }
        return new Result(provider, model, Map.copyOf(suggestions));
    }
}
