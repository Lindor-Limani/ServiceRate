package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "ux_reviews_booking_id", columnList = "booking_id", unique = true)
})
public class Review extends AuditableEntity {

    // Jede Bewertung gehört zu einer konkreten Buchung
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Column(nullable = false)
    private Integer rating; // z.B. 1 bis 5 Sterne

    @Column(columnDefinition = "text")
    private String comment;
}
