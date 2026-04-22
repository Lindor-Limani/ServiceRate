package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "service_offerings")
public class ServiceOffering extends AuditableEntity {

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "service_mode", nullable = false)
    private String serviceMode;

    @Column(name = "pricing_model", nullable = false)
    private String pricingModel;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "min_booking_hours")
    private Double minBookingHours;

    @Column(name = "min_booking_days")
    private Integer minBookingDays;

    @Column(name = "lead_time_hours")
    private Integer leadTimeHours;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @OneToMany(mappedBy = "serviceOffering", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ServiceOfferingPrice> prices = new HashSet<>();
}
