package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.ReportRequest;
import at.hcw.serviceratebackend.dto.ReportResponse;
import at.hcw.serviceratebackend.model.entity.Report;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ReportRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReportResponse create(ReportRequest request, String reporterEmail) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
        if (!"ACTIVE".equals(reporter.getStatus())) {
            throw new IllegalArgumentException("Dein Account wurde deaktiviert. Bitte kontaktiere den Support.");
        }
        String targetType = normalizeTargetType(request.targetType());
        if (request.targetId() == null) {
            throw new IllegalArgumentException("Ziel fehlt.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Grund fehlt.");
        }
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(reporter);
        report.setTargetType(targetType);
        report.setTargetId(request.targetId());
        report.setReason(request.reason().trim());
        report.setDetails(request.details() == null || request.details().isBlank() ? null : request.details().trim());
        report.setStatus("OPEN");
        return toResponse(reportRepository.save(report));
    }

    private String normalizeTargetType(String targetType) {
        String normalized = targetType == null ? "" : targetType.trim().toUpperCase();
        if (!normalized.equals("SERVICE") && !normalized.equals("REVIEW") && !normalized.equals("PROVIDER")) {
            throw new IllegalArgumentException("Ungültiger Report-Typ.");
        }
        return normalized;
    }

    private ReportResponse toResponse(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporter().getEmail(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
