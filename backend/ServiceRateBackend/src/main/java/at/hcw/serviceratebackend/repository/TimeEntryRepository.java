package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    List<TimeEntry> findByBookingIdOrderByWorkDateDescCreatedAtDesc(UUID bookingId);
    List<TimeEntry> findByBooking_ServiceOffering_Provider_Id(UUID providerId);
}
