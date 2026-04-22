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
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id", nullable = false)
    private User reviewerUser;

    @Column(name = "reviewee_type", nullable = false)
    private String revieweeType;

    @Column(name = "reviewee_id", nullable = false)
    private UUID revieweeId;

    @Column(nullable = false)
    private Integer rating;

    @Column
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
