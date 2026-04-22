package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends AuditableEntity {

    @Column(name = "booking_number", nullable = false, unique = true)
    private String bookingNumber;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "buyer_type", nullable = false)
    private String buyerType;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_type", nullable = false)
    private String sellerType;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "booked_at", insertable = false, updatable = false)
    private OffsetDateTime bookedAt;

    @Column(name = "service_start_at")
    private OffsetDateTime serviceStartAt;

    @Column(name = "service_end_at")
    private OffsetDateTime serviceEndAt;

    @Column(nullable = false)
    private String status;
}
