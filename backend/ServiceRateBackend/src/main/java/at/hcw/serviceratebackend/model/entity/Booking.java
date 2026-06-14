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
}