package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.service.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import at.hcw.serviceratebackend.dto.UpdateServiceRequest;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    // M6: POST Endpunkt (Create)
    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(@RequestBody CreateServiceRequest request, Authentication authentication) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(service.createForProviderEmail(request, email));
    }

    // M6: GET Endpunkt (Read)
    @GetMapping
    public ResponseEntity<List<ServiceOfferingResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // Gibt nur die Services des eingeloggten Providers zurück (E-Mail kommt aus dem JWT)
    @GetMapping("/my")
    public ResponseEntity<List<ServiceOfferingResponse>> getMyServices(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(service.getMyServices(email));
    }



    // M6: DELETE Endpunkt (Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
    // M6: PUT-Request
    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> updateService(
            @PathVariable("id") java.util.UUID id,
            @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(service.updateService(id, request));
    }
}
