package at.hcw.serviceratebackend.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "organization_profiles")
public class OrganizationProfile {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column
    private String headline;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "team_size_exact")
    private Integer teamSizeExact;

    @Column(name = "remote_available", nullable = false)
    private Boolean remoteAvailable;

    @Column(name = "on_site_available", nullable = false)
    private Boolean onSiteAvailable;

    @Column(name = "emergency_service", nullable = false)
    private Boolean emergencyService;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
