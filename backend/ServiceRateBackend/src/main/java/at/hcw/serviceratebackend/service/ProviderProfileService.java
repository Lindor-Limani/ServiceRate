package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.ProviderProfileResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderProfileService {

    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceOfferingService serviceOfferingService;

    public ProviderProfileResponse getProviderProfile(UUID providerId) {
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Anbieter nicht gefunden"));

        List<ServiceOfferingResponse> services = serviceOfferingRepository.findByProviderId(providerId).stream()
                .map(service -> serviceOfferingService.getById(service.getId()))
                .toList();

        long reviewCount = services.stream()
                .mapToLong(s -> s.reviewCount() == null ? 0 : s.reviewCount())
                .sum();
        double weightedRatingSum = services.stream()
                .mapToDouble(s -> (s.averageRating() == null ? 0 : s.averageRating()) * (s.reviewCount() == null ? 0 : s.reviewCount()))
                .sum();
        double averageRating = reviewCount > 0 ? weightedRatingSum / reviewCount : 0.0;
        int trustScore = services.isEmpty()
                ? 0
                : (int) Math.round(services.stream().mapToInt(s -> s.trustScore() == null ? 0 : s.trustScore()).average().orElse(0.0));
        List<String> categories = services.stream()
                .map(ServiceOfferingResponse::category)
                .distinct()
                .toList();

        return new ProviderProfileResponse(
                provider.getId(),
                fullName(provider),
                provider.getStatus(),
                services.size(),
                averageRating,
                reviewCount,
                trustScore,
                categories,
                services
        );
    }

    private String fullName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Unbekannter Anbieter" : name;
    }
}
