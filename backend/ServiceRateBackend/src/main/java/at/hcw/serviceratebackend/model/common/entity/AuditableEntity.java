package at.hcw.serviceratebackend.model.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
public class AuditableEntity extends BaseEntity{
    @Column(name="created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name="updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
