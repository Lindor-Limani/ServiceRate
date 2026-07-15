package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Spring Boot generiert die SQL-Abfrage ("SELECT * FROM bookings WHERE provider_id = ...")
    // ganz automatisch nur anhand dieses Namens!
    java.util.List<Booking> findByServiceOffering_Provider_Id(UUID providerId);

    // Holt alle Buchungen für einen bestimmten Kunden
    java.util.List<Booking> findByCustomer_Id(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForReviewCreation(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForStatusUpdate(@Param("id") UUID id);

}
