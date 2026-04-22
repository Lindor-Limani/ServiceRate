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
@Table(name = "orders")
public class OrderEntity extends AuditableEntity {

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "buyer_type", nullable = false)
    private String buyerType;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_type", nullable = false)
    private String sellerType;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "service_start_at")
    private OffsetDateTime serviceStartAt;

    @Column(name = "service_end_at")
    private OffsetDateTime serviceEndAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
