package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UpdateServiceRequest;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final LocationValidationService locationValidationService;

    public ServiceOfferingResponse create(CreateServiceRequest request) {
        User provider = userRepository.findById(request.providerId())
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));
        return createForProvider(request, provider);
    }

    public ServiceOfferingResponse createForProviderEmail(CreateServiceRequest request, String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));
        return createForProvider(request, provider);
    }

    private ServiceOfferingResponse createForProvider(CreateServiceRequest request, User provider) {
        if (!"PROVIDER".equals(provider.getAccountType())) {
            throw new IllegalArgumentException("Nur Anbieter dürfen Services erstellen.");
        }
        if (!provider.isEmailVerified()) {
            throw new IllegalArgumentException("Bitte verifiziere zuerst deine E-Mail-Adresse.");
        }

        // PLZ über die externe Zippopotam.us-API in einen Ortsnamen auflösen (400, falls ungültig)
        String location = locationValidationService.resolveCityName(request.zipCode());

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setProvider(provider);
        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(request.category());
        service.setPrice(request.price());
        service.setEstimatedHours(request.estimatedHours());
        service.setImageUrl(blankToNull(request.imageUrl()));
        service.setDeliverableType(normalizeDeliverableType(request.deliverableType()));
        service.setLocation(location);
        service.setStatus("ACTIVE");

        return mapToResponse(serviceRepository.save(service));
    }

    public List<ServiceOfferingResponse> getAll() {
        return serviceRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ServiceOfferingResponse getById(UUID id) {
        return serviceRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));
    }

    // Nur die Services des eingeloggten Providers (anhand der E-Mail aus dem JWT-Subject)
    public List<ServiceOfferingResponse> getMyServices(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
        return serviceRepository.findByProviderId(provider.getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void delete(UUID id) {
        serviceRepository.deleteById(id);
    }

    public ServiceOfferingResponse updateService(UUID id, UpdateServiceRequest request) {
        ServiceOffering service = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));

        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(request.category());
        service.setPrice(request.price());
        service.setEstimatedHours(request.estimatedHours());
        service.setImageUrl(blankToNull(request.imageUrl()));
        service.setDeliverableType(normalizeDeliverableType(request.deliverableType()));

        return mapToResponse(serviceRepository.save(service));
    }

    // Wandelt eine Entity ins Antwort-DTO um und reichert sie mit den Live-Bewertungen an
    private ServiceOfferingResponse mapToResponse(ServiceOffering service) {
        // findAverageRatingByServiceId kann null sein, wenn es noch keine Reviews gibt -> sauber auf 0.0 mappen
        Double avg = reviewRepository.findAverageRatingByServiceId(service.getId());
        double averageRating = (avg != null) ? avg : 0.0;
        Long reviewCount = reviewRepository.findReviewCountByServiceId(service.getId());
        var reviews = reviewRepository.findByBookingServiceOfferingId(service.getId()).stream()
                .map(reviewService::toResponse)
                .toList();
        int trustScore = calculateTrustScore(averageRating, reviewCount, service.getStatus());

        return new ServiceOfferingResponse(
                service.getId(),
                service.getProvider().getId(),
                service.getProvider().getFirstName() + " " + service.getProvider().getLastName(),
                service.getProvider().getProfileImageUrl(),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getPrice(),
                service.getEstimatedHours(),
                service.getImageUrl(),
                service.getDeliverableType(),
                service.getStatus(),
                service.getLocation(),
                averageRating,
                reviewCount,
                trustScore,
                reviews
        );
    }

    private int calculateTrustScore(double averageRating, long reviewCount, String status) {
        double ratingPoints = (averageRating / 5.0) * 70.0;
        double volumePoints = (Math.min(reviewCount, 20) / 20.0) * 20.0;
        double statusPoints = "ACTIVE".equals(status) ? 10.0 : 0.0;
        return (int) Math.round(Math.min(100.0, ratingPoints + volumePoints + statusPoints));
    }

    private String normalizeDeliverableType(String deliverableType) {
        String normalized = deliverableType == null || deliverableType.isBlank()
                ? "ON_SITE"
                : deliverableType.trim().toUpperCase();
        if (!normalized.equals("ON_SITE") && !normalized.equals("DIGITAL") && !normalized.equals("HYBRID")) {
            throw new IllegalArgumentException("Ungültige Lieferart.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
