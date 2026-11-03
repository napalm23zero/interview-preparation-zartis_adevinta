package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

// Covers local identity creation and repair, with guard rails against wandering into another realm.
class DemoIdentityProvisionerTest {
    @Test void recreatesMissingUsersAndSetsTheirPasswords() throws Exception {
        List<String> operations = new ArrayList<>();
        var accounts = new java.util.HashMap<String, com.fasterxml.jackson.databind.JsonNode>();
        var bindings = new java.util.ArrayList<String>();
        var mapper = new ObjectMapper();
        DemoIdentityProvisioner.Transport identity = (path, method, token, contentType, body) -> {
            operations.add(method + " " + path);
            if (path.endsWith("/token")) return new DemoIdentityProvisioner.Result(200, "{\"access_token\":\"test\"}");
            if (method.equals("GET") && path.contains("?username=")) {
                String username = java.net.URLDecoder.decode(path.substring(path.indexOf("username=") + 9, path.indexOf("&exact")), java.nio.charset.StandardCharsets.UTF_8);
                return new DemoIdentityProvisioner.Result(200, mapper.writeValueAsString(accounts.containsKey(username) ? List.of(accounts.get(username)) : List.of()));
            }
            if (method.equals("GET")) return new DemoIdentityProvisioner.Result(404, "");
            if (method.equals("POST")) {
                var account = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(body);
                account.put("id", java.util.UUID.randomUUID().toString());
                accounts.put(account.path("username").asText(), account);
                return new DemoIdentityProvisioner.Result(201, "");
            }
            return new DemoIdentityProvisioner.Result(204, "");
        };
        DemoIdentityProvisioner provisioner = new DemoIdentityProvisioner(
                "http://identity:8080/realms/loopmarket-dev", "admin", "password", mapper, identity,
                (expected, actual, created) -> { assertTrue(created); bindings.add(expected + ":" + actual); });
        provisioner.reconcile();
        assertEquals(4, bindings.size());
        assertEquals(4, operations.stream().filter(s -> s.equals("POST /admin/realms/loopmarket-dev/users")).count());
        assertEquals(4, operations.stream().filter(s -> s.endsWith("/reset-password")).count());
    }

    @Test void refusesNonDevelopmentIdentityServer() {
        assertThrows(IllegalArgumentException.class, () -> new DemoIdentityProvisioner(
                "https://example.com/realms/loopmarket-dev", "admin", "password", new ObjectMapper()));
    }

    @Test void reenablesDisabledDemoUsersWithoutRecreatingTheRealm() throws Exception {
        List<String> operations = new ArrayList<>();
        DemoIdentityProvisioner.Transport identity = (path, method, token, contentType, body) -> {
            operations.add(method + " " + path);
            if (path.endsWith("/token")) return new DemoIdentityProvisioner.Result(200, "{\"access_token\":\"test\"}");
            if (method.equals("GET")) {
                String username = switch (path.substring(path.length() - 3)) {
                    case "201" -> "darth.vader"; case "202" -> "luke.skywalker";
                    case "203" -> "leia.organa"; default -> "han.solo";
                };
                return new DemoIdentityProvisioner.Result(200, "{\"username\":\"" + username + "\",\"enabled\":false}");
            }
            return new DemoIdentityProvisioner.Result(204, "");
        };
        DemoIdentityProvisioner provisioner = new DemoIdentityProvisioner(
                "http://identity:8080/realms/loopmarket-dev", "admin", "password", new ObjectMapper(), identity);
        provisioner.reconcile();
        assertEquals(4, operations.stream().filter(s -> s.startsWith("PUT ") && !s.endsWith("/reset-password")).count());
        assertEquals(4, operations.stream().filter(s -> s.endsWith("/reset-password")).count());
        assertFalse(operations.stream().anyMatch(s -> s.equals("POST /admin/realms/loopmarket-dev/users")));
    }

    @Test void refusesUnlinkedExistingUsername() {
        List<String> operations = new ArrayList<>();
        DemoIdentityProvisioner.Transport identity = (path, method, token, contentType, body) -> {
            operations.add(method + " " + path);
            if (path.endsWith("/token")) return new DemoIdentityProvisioner.Result(200, "{\"access_token\":\"test\"}");
            if (path.contains("?username=")) return new DemoIdentityProvisioner.Result(200,
                    "[{\"id\":\"b74d6721-c123-4761-b099-aabf93558205\",\"username\":\"darth.vader\",\"email\":\"darth.vader@example.invalid\"}]");
            return new DemoIdentityProvisioner.Result(404, "");
        };
        var provisioner = new DemoIdentityProvisioner("http://identity:8080/realms/loopmarket-dev", "admin", "password", new ObjectMapper(), identity,
                (expected, actual, created) -> { assertFalse(created); throw new IllegalStateException("Account is not linked"); });
        assertThrows(IllegalStateException.class, provisioner::reconcile);
        assertFalse(operations.stream().anyMatch(s -> s.startsWith("PUT ")));
    }

    @Test void refusesToModifyAnUnexpectedAccountAtADemoId() {
        List<String> operations = new ArrayList<>();
        DemoIdentityProvisioner.Transport identity = (path, method, token, contentType, body) -> {
            operations.add(method + " " + path);
            if (path.endsWith("/token")) return new DemoIdentityProvisioner.Result(200, "{\"access_token\":\"test\"}");
            return new DemoIdentityProvisioner.Result(200, "{\"username\":\"someone.else\",\"enabled\":true}");
        };
        DemoIdentityProvisioner provisioner = new DemoIdentityProvisioner(
                "http://identity:8080/realms/loopmarket-dev", "admin", "password", new ObjectMapper(), identity);
        assertThrows(IllegalStateException.class, provisioner::reconcile);
        assertFalse(operations.stream().anyMatch(s -> s.startsWith("PUT ")));
    }
}
