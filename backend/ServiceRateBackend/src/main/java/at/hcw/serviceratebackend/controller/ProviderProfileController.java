package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.ProviderProfileResponse;
import at.hcw.serviceratebackend.service.ProviderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;

    @GetMapping("/{providerId}")
    public ProviderProfileResponse getProviderProfile(@PathVariable UUID providerId) {
        return providerProfileService.getProviderProfile(providerId);
    }
}
