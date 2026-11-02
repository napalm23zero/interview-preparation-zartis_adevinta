package dev.hustletech.loopmarket.app.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

// Opt-in test uses a real local model; its prose remains a suggestion, not Sith-certified fact.
@EnabledIfEnvironmentVariable(named = "LOOPMARKET_GATEWAY_E2E_URL", matches = ".+")
class GatewayLiveIntegrationTest {
    @Test void localGatewayReturnsValidatedModelMetadataAndSuggestions() throws Exception {
        var client = new GatewayClient(System.getenv("LOOPMARKET_GATEWAY_E2E_URL"), new ObjectMapper());
        var result = client.generate(Map.of("title", "Used wooden chair", "description", "A small wooden chair in good condition."));
        assertEquals("ollama", result.provider());
        assertNotNull(result.model());
        assertFalse(result.suggestions().isEmpty());
    }
}
