package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.AdminStatsResponse;
import at.hcw.serviceratebackend.dto.AdminBookingResponse;
import at.hcw.serviceratebackend.dto.AdminReviewResponse;
import at.hcw.serviceratebackend.dto.ReportResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UpdateReportStatusRequest;
import at.hcw.serviceratebackend.dto.UpdateUserStatusRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.users();
    }

    @GetMapping("/services")
    public List<ServiceOfferingResponse> services() {
        return adminService.services();
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> bookings() {
        return adminService.bookings();
    }

    @GetMapping("/reviews")
    public List<AdminReviewResponse> reviews() {
        return adminService.reviews();
    }

    @GetMapping("/reports")
    public List<ReportResponse> reports() {
        return adminService.reports();
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse setUserStatus(@PathVariable java.util.UUID id, @RequestBody UpdateUserStatusRequest request) {
        return adminService.setUserActive(id, request.active());
    }

    @PatchMapping("/services/{id}/status")
    public ServiceOfferingResponse setServiceStatus(@PathVariable java.util.UUID id, @RequestBody java.util.Map<String, String> request) {
        return adminService.setServiceStatus(id, request.get("status"));
    }

    @PatchMapping("/reports/{id}/status")
    public ReportResponse setReportStatus(@PathVariable java.util.UUID id, @RequestBody UpdateReportStatusRequest request) {
        return adminService.setReportStatus(id, request.status());
    }
}
