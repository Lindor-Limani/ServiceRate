package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByBuyerOrganization_Id(UUID organizationId);
}
