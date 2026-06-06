package at.hcw.serviceratebackend.model.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
public class AuditableEntity extends BaseEntity {

    // Wir haben insertable=false und updatable=false entfernt!
    @Column(name="created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name="updated_at")
    private OffsetDateTime updatedAt;

    // Diese Methode wird von Spring Boot AUTOMATISCH aufgerufen,
    // kurz bevor ein Eintrag (z.B. User) das ERSTE MAL gespeichert wird.
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Diese Methode wird AUTOMATISCH aufgerufen,
    // wenn ein Eintrag später verändert wird (z.B. Passwort geändert).
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}