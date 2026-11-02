package dev.hustletech.loopmarket.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityTest {
    private static final UUID USER_ID = UUID.fromString("01993b57-d803-7000-8000-000000000001");
    private static final UUID PERSON_ID = UUID.fromString("01993b57-d803-7000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void linksAUserToAPersonWithoutAssumingTheProviderSubjectIsAUuid() {
        Person person = new Person(PERSON_ID, "Rodrigo", NOW, NOW, null);
        User user = new User(USER_ID, person.id(), "https://issuer.example", "provider-subject", NOW, NOW, null);
        assertEquals(person.id(), user.personId());
        assertEquals("provider-subject", user.externalSubject());
        assertNull(user.deletedAt());
    }

    @Test
    void rejectsInvalidIdsAndBlankIdentityFields() {
        UUID v4 = UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertThrows(IllegalArgumentException.class, () -> new Person(v4, "Rodrigo", NOW, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new Person(PERSON_ID, " ", NOW, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new User(USER_ID, v4, "issuer", "subject", NOW, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new User(USER_ID, PERSON_ID, "issuer", " ", NOW, NOW, null));
    }
}
