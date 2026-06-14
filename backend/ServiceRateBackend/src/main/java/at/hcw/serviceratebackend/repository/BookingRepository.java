package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Spring Boot generiert die SQL-Abfrage ("SELECT * FROM bookings WHERE provider_id = ...")
    // ganz automatisch nur anhand dieses Namens!
    java.util.List<Booking> findByServiceOffering_Provider_Id(UUID providerId);

    // Holt alle Buchungen für einen bestimmten Kunden
    java.util.List<Booking> findByCustomer_Id(UUID customerId);

}