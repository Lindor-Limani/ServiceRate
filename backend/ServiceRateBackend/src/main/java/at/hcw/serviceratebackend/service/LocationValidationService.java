package at.hcw.serviceratebackend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class LocationValidationService {

    private static final String ZIPPOPOTAM_URL = "https://api.zippopotam.us/at/{zipCode}";

    private final RestTemplate restTemplate;

    public LocationValidationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Löst eine österreichische PLZ in einen Ortsnamen auf.
    // Wirft IllegalArgumentException ("Ungültige Postleitzahl") -> 400, wenn die PLZ nicht gefunden wird.
    public String resolveCityName(String zipCode) {
        ZippopotamResponse response;
        try {
            response = restTemplate.getForObject(ZIPPOPOTAM_URL, ZippopotamResponse.class, zipCode);
        } catch (RestClientException e) {
            // Zippopotam liefert 404 bei unbekannter PLZ -> RestClientException
            throw new IllegalArgumentException("Ungültige Postleitzahl");
        }

        if (response == null || response.places() == null || response.places().isEmpty()) {
            throw new IllegalArgumentException("Ungültige Postleitzahl");
        }

        return response.places().get(0).placeName();
    }

    // ── Minimal-DTOs für die Zippopotam-Antwort ──────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZippopotamResponse(
            @JsonProperty("post code") String postCode,
            @JsonProperty("places") List<Place> places
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            @JsonProperty("place name") String placeName
    ) {}
}
