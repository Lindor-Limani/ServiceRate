package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    long countByStatus(String status);
    List<Report> findAllByOrderByCreatedAtDesc();
}
