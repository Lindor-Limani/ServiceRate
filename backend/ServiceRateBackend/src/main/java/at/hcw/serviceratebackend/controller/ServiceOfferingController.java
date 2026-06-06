package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.service.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    // M6: POST Endpunkt (Create)
    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(@RequestBody CreateServiceRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    // M6: GET Endpunkt (Read)
    @GetMapping
    public ResponseEntity<List<ServiceOfferingResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // M6: PUT Endpunkt (Update) - Beachte den {id} Pfad!
    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> update(@PathVariable UUID id, @RequestBody CreateServiceRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // M6: DELETE Endpunkt (Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}