package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DemoIdentityProvisionerTest {
    @Test void recreatesMissingUsersAndSetsTheirPasswords() throws Exception {
        List<String> operations = new ArrayList<>();
        DemoIdentityProvisioner.Transport identity = (path, method, token, contentType, body) -> {
            operations.add(method + " " + path);
            if (path.endsWith("/token")) return new DemoIdentityProvisioner.Result(200, "{\"access_token\":\"test\"}");
            if (method.equals("GET")) return new DemoIdentityProvisioner.Result(404, "");
            if (method.equals("POST")) return new DemoIdentityProvisioner.Result(201, "");
            return new DemoIdentityProvisioner.Result(204, "");
        };
        DemoIdentityProvisioner provisioner = new DemoIdentityProvisioner(
                "http://identity:8080/realms/loopmarket-dev", "admin", "password", new ObjectMapper(), identity);
        provisioner.reconcile();
        assertEquals(2, operations.stream().filter(s -> s.equals("POST /admin/realms/loopmarket-dev/users")).count());
        assertEquals(2, operations.stream().filter(s -> s.endsWith("/reset-password")).count());
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
                String username = path.endsWith("201") ? "darth.vader" : "luke.skywalker";
                return new DemoIdentityProvisioner.Result(200, "{\"username\":\"" + username + "\",\"enabled\":false}");
            }
            return new DemoIdentityProvisioner.Result(204, "");
        };
        DemoIdentityProvisioner provisioner = new DemoIdentityProvisioner(
                "http://identity:8080/realms/loopmarket-dev", "admin", "password", new ObjectMapper(), identity);
        provisioner.reconcile();
        assertEquals(2, operations.stream().filter(s -> s.startsWith("PUT ") && !s.endsWith("/reset-password")).count());
        assertEquals(2, operations.stream().filter(s -> s.endsWith("/reset-password")).count());
        assertFalse(operations.stream().anyMatch(s -> s.equals("POST /admin/realms/loopmarket-dev/users")));
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
