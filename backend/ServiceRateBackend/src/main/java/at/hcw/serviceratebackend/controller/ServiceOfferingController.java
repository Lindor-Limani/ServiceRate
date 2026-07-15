package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.PageResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.service.ImageResource;
import at.hcw.serviceratebackend.service.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import at.hcw.serviceratebackend.dto.UpdateServiceRequest;


import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    public ResponseEntity<PageResponse<ServiceOfferingResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") Double minRating,
            @RequestParam(defaultValue = "recommended") String sort) {
        return ResponseEntity.ok(service.search(page, size, q, category, location, maxPrice, minRating, sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getPrimaryImage(@PathVariable("id") UUID id) {
        return service.getPrimaryImage(id)
                .map(this::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, Authentication authentication) {
        String email = (String) authentication.getPrincipal();
        service.deleteForProviderEmail(id, email);
        return ResponseEntity.ok().build();
    }
    // M6: PUT-Request
    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingResponse> updateService(
            @PathVariable("id") java.util.UUID id,
            @RequestBody UpdateServiceRequest request,
            Authentication authentication) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(service.updateServiceForProviderEmail(id, request, email));
    }

    private ResponseEntity<byte[]> imageResponse(ImageResource image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(image.bytes());
    }
}
