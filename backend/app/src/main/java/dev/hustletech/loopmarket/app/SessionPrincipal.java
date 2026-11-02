package dev.hustletech.loopmarket.app;

import java.security.Principal;
import java.util.UUID;

// Request-scoped identity used by authorization after the opaque token passes validation.
record SessionPrincipal(UUID id, String role) implements Principal {
    @Override public String getName() { return id.toString(); }
}
