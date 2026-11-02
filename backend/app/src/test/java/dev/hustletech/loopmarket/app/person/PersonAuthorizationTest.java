package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.adapter.http.GenericCrudResource;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Confirms ordinary users cannot use admin-only person endpoints; nice try, young Sith.
class PersonAuthorizationTest {
    @Test void regularUserCannotReadOrChangePeople() {
        PersonResource resource = new PersonResource(null);
        SecurityContext user = new SecurityContext() {
            public Principal getUserPrincipal() { return () -> "user"; }
            public boolean isUserInRole(String role) { return false; }
            public boolean isSecure() { return true; }
            public String getAuthenticationScheme() { return "Bearer"; }
        };
        for (GenericCrudResource.Action action : GenericCrudResource.Action.values()) {
            assertThrows(ForbiddenException.class, () -> resource.authorizedService(user, action, null));
        }
    }
}
