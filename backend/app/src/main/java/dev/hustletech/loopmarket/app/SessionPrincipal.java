package dev.hustletech.loopmarket.app;

import java.security.Principal;
import java.util.UUID;

record SessionPrincipal(UUID id, String role) implements Principal {
    @Override public String getName() { return id.toString(); }
}
