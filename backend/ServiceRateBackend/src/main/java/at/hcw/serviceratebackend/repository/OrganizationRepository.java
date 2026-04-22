package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
