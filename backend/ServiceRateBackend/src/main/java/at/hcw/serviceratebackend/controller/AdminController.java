package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.AdminStatsResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
