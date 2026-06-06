package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceRepository;
    private final UserRepository userRepository;

    // CREATE (POST)
    public ServiceOfferingResponse create(CreateServiceRequest request) {
        User provider = userRepository.findById(request.providerId())
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setProvider(provider);
        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(request.category());
        service.setPrice(request.price());
        service.setStatus("ACTIVE");

        ServiceOffering saved = serviceRepository.save(service);
        return mapToResponse(saved);
    }

    // READ (GET)
    public List<ServiceOfferingResponse> getAll() {
        return serviceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // DELETE (DELETE)
    public void delete(UUID id) {
        serviceRepository.deleteById(id);
    }

    // UPDATE (PUT)
    public ServiceOfferingResponse update(UUID id, CreateServiceRequest request) {
        ServiceOffering service = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));

        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(request.category());
        service.setPrice(request.price());

        return mapToResponse(serviceRepository.save(service));
    }

    // Hilfsmethode zum Umwandeln von Datenbank-Entity zu DTO
    private ServiceOfferingResponse mapToResponse(ServiceOffering service) {
        return new ServiceOfferingResponse(
                service.getId(),
                service.getProvider().getFirstName() + " " + service.getProvider().getLastName(),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getPrice(),
                service.getStatus()
        );
    }
}