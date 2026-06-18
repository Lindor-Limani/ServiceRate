package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    // Durchschnittliche Sternebewertung für ein ServiceOffering (via review -> booking -> serviceOffering)
    // Kann NULL liefern, wenn es noch keine Reviews gibt!
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.booking.serviceOffering.id = :serviceId")
    Double findAverageRatingByServiceId(@Param("serviceId") UUID serviceId);

    // Anzahl der Reviews für ein ServiceOffering
    @Query("SELECT COUNT(r) FROM Review r WHERE r.booking.serviceOffering.id = :serviceId")
    Long findReviewCountByServiceId(@Param("serviceId") UUID serviceId);

    List<Review> findByBookingId(UUID bookingId);

    List<Review> findByBookingIdIn(Collection<UUID> bookingIds);

    List<Review> findByBookingServiceOfferingId(UUID serviceId);
}
