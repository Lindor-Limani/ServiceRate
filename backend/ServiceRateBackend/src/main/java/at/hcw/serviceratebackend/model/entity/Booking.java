package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_offering_id", nullable = false)
    private ServiceOffering serviceOffering;

    @Column(name = "service_date", nullable = false)
    private OffsetDateTime serviceDate;

    // Vom Kunden gewählter Wunschtermin
    @Column(name = "booking_date")
    private LocalDate bookingDate;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, COMPLETED

    @Column(name = "actual_hours")
    private Double actualHours;

    @Column(name = "provider_notes", columnDefinition = "text")
    private String providerNotes;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    @Column(name = "delivery_url", length = 1000)
    private String deliveryUrl;

    @Column(name = "delivery_label")
    private String deliveryLabel;

    @Column(name = "delivery_expires_at")
    private OffsetDateTime deliveryExpiresAt;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "UNPAID"; // UNPAID, CHECKOUT_CREATED, PAID, REFUNDED

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(name = "payment_provider")
    private String paymentProvider = "MANUAL";

    @Column(name = "payment_note", columnDefinition = "text")
    private String paymentNote;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}
