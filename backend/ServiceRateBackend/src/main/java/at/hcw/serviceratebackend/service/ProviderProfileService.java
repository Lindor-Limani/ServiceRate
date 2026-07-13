package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.ProviderProfileResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderProfileService {

    private final UserRepository userRepository;
    private final ServiceOfferingService serviceOfferingService;

    public ProviderProfileResponse getProviderProfile(UUID providerId) {
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Anbieter nicht gefunden"));

        List<ServiceOfferingResponse> services = serviceOfferingService.getActiveSummariesByProviderId(providerId);

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
                provider.getProfileImageUrl(),
                provider.getStatus(),
                services.size(),
                averageRating,
                reviewCount,
                trustScore,
                categories,
                services
        );
    }

    public Optional<ImageResource> getProviderAvatar(UUID providerId) {
        return userRepository.findById(providerId)
                .flatMap(user -> decodeDataImage(user.getProfileImageUrl()));
    }

    private String fullName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Unbekannter Anbieter" : name;
    }

    private Optional<ImageResource> decodeDataImage(String value) {
        String dataUrl = value == null || value.isBlank() ? null : value.trim();
        if (dataUrl == null || !dataUrl.regionMatches(true, 0, "data:image/", 0, 11)) {
            return Optional.empty();
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).toLowerCase().contains(";base64")) {
            return Optional.empty();
        }
        String metadata = dataUrl.substring(5, comma).toLowerCase();
        String contentType = metadata.substring(0, metadata.indexOf(';'));
        if (!List.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(contentType)) {
            return Optional.empty();
        }
        return Optional.of(new ImageResource(Base64.getDecoder().decode(dataUrl.substring(comma + 1)), contentType));
    }
}
