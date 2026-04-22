package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.BaseEntity;
import at.hcw.serviceratebackend.model.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "conversation_threads")
public class ConversationThread extends BaseEntity {

    @Column(name = "context_type")
    private String contextType;

    @Column(name = "context_id")
    private UUID contextId;

    @Column(name = "thread_type", nullable = false)
    private String threadType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
