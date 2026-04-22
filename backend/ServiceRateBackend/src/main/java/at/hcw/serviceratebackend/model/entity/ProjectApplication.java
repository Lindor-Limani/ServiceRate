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
@Table(name = "project_applications")
public class ProjectApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_position_id")
    private ProjectPosition projectPosition;

    @Column(name = "applicant_provider_type", nullable = false)
    private String applicantProviderType;

    @Column(name = "applicant_provider_id", nullable = false)
    private UUID applicantProviderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedByUser;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "pricing_model", nullable = false)
    private String pricingModel;

    @Column(name = "proposed_total_net")
    private Double proposedTotalNet;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(nullable = false)
    private String status;

    @Column(name = "submitted_at", insertable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
