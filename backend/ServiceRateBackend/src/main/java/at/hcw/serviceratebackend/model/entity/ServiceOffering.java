package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "service_offerings")
public class ServiceOffering extends AuditableEntity {

    // Welcher Handwerker bietet das an?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String category; // z.B. "CLEANING", "REPAIR" (als einfacher String statt Tabelle)

    @Column(nullable = false)
    private Double price; // Simpler Fest- oder Stundenpreis

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "deliverable_type")
    private String deliverableType = "ON_SITE"; // ON_SITE, DIGITAL, HYBRID

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "EUR";

    // Ortsname, ermittelt aus der PLZ über die Zippopotam.us-API
    @Column
    private String location;

    @Column(nullable = false)
    private String status = "ACTIVE";
}
