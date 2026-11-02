package dev.hustletech.loopmarket.app.account;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "users")
// Maps a local authorization account to a provider subject; authVersion invalidates older sessions after changes.
public class AccountEntity extends GenericEntity {
    @Column(name = "person_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID personId;
    @Column(nullable = false, length = 255)
    private String issuer;
    @Column(name = "external_subject", nullable = false, length = 255)
    private String externalSubject;
    @Column(name = "account_role", nullable = false, length = 16)
    private String role;
    @Version @Column(name = "auth_version", nullable = false)
    private long authVersion;

    protected AccountEntity() {}
    AccountEntity(UUID personId, String issuer, String subject, String role) { update(personId, issuer, subject, role); }
    void update(UUID personId, String issuer, String subject, String role) {
        this.personId = personId; this.issuer = issuer; this.externalSubject = subject; this.role = role;
    }
    UUID personId() { return personId; }
    String issuer() { return issuer; }
    String externalSubject() { return externalSubject; }
    String role() { return role; }
}
