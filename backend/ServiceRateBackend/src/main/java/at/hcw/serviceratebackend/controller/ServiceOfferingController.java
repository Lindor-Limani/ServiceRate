package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.service.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ServiceOfferingResponse> create(@RequestBody CreateServiceRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    // M6: GET Endpunkt (Read)
    @GetMapping
    public ResponseEntity<List<ServiceOfferingResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }



    // M6: DELETE Endpunkt (Delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
    // M6: PUT-Request
    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> updateService(
            @PathVariable java.util.UUID id,
            @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(service.updateService(id, request));
    }
}