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
    private final LocationValidationService locationValidationService;

    public ServiceOfferingResponse create(CreateServiceRequest request) {
        User provider = userRepository.findById(request.providerId())
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));

        // PLZ über die externe Zippopotam.us-API in einen Ortsnamen auflösen (400, falls ungültig)
        String location = locationValidationService.resolveCityName(request.zipCode());

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setProvider(provider);
        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(request.category());
        service.setPrice(request.price());
        service.setLocation(location);
        service.setStatus("ACTIVE");

        return mapToResponse(serviceRepository.save(service));
    }

    public List<ServiceOfferingResponse> getAll() {
        return serviceRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
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

        return mapToResponse(serviceRepository.save(service));
    }

    // Wandelt eine Entity ins Antwort-DTO um und reichert sie mit den Live-Bewertungen an
    private ServiceOfferingResponse mapToResponse(ServiceOffering service) {
        // findAverageRatingByServiceId kann null sein, wenn es noch keine Reviews gibt -> sauber auf 0.0 mappen
        Double avg = reviewRepository.findAverageRatingByServiceId(service.getId());
        double averageRating = (avg != null) ? avg : 0.0;
        Long reviewCount = reviewRepository.findReviewCountByServiceId(service.getId());

        return new ServiceOfferingResponse(
                service.getId(),
                service.getProvider().getFirstName() + " " + service.getProvider().getLastName(),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getPrice(),
                service.getStatus(),
                service.getLocation(),
                averageRating,
                reviewCount
        );
    }
}