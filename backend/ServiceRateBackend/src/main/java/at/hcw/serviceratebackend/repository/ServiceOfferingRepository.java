package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    // Die neue, simple Methode: Finde alle Services, die einem bestimmten Handwerker (User) gehören.
    // Das brauchen wir später für das "Provider Dashboard" (S2).
    List<ServiceOffering> findByProviderId(UUID providerId);
}