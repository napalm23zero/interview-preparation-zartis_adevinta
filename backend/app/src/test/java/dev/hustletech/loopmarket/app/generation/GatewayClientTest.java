package dev.hustletech.loopmarket.app.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GatewayClientTest {
    @Test void acceptsValidatedSuggestionsAndRejectsUntrustedOutput() throws Exception {
        AtomicReference<String> body = new AtomicReference<>("{\"provider\":\"ollama\",\"model\":\"local\",\"suggestions\":{\"title\":\"Book\",\"category\":\"BOOKS\"}}");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/generate", exchange -> {
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = new GatewayClient("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
            var result = client.generate(Map.of("title", "Book"));
            assertEquals("ollama", result.provider());
            assertEquals("Book", result.suggestions().get("title"));
            assertEquals("BOOKS", result.suggestions().get("category"));
            body.set("{\"provider\":\"ollama\",\"model\":\"local\",\"suggestions\":{\"title\":\"<script>\"}}");
            assertThrows(java.io.IOException.class, () -> client.generate(Map.of("title", "Book")));
            body.set("{\"provider\":\"ollama\",\"model\":\"local\",\"suggestions\":{\"price\":\"1\"}}");
            assertThrows(java.io.IOException.class, () -> client.generate(Map.of("title", "Book")));
            body.set("{\"provider\":\"ollama\",\"model\":\"local\",\"suggestions\":{\"category\":\"SECRET_PLANS\"}}");
            assertThrows(java.io.IOException.class, () -> client.generate(Map.of("title", "Book")));
            body.set("x".repeat(65537));
            assertThrows(java.io.IOException.class, () -> client.generate(Map.of("title", "Book")));
        } finally { server.stop(0); }
    }
}
