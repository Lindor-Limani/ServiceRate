package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.ReportRequest;
import at.hcw.serviceratebackend.dto.ReportResponse;
import at.hcw.serviceratebackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ReportResponse create(@RequestBody ReportRequest request, Authentication authentication) {
        return reportService.create(request, (String) authentication.getPrincipal());
    }
}
