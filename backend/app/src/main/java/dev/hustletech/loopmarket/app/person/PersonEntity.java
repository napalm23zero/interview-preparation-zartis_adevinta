package dev.hustletech.loopmarket.app.person;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "persons")
public class PersonEntity extends GenericEntity {
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    protected PersonEntity() {}
    PersonEntity(String displayName) { this.displayName = displayName; }
    String displayName() { return displayName; }
    void rename(String displayName) { this.displayName = displayName; }
}
