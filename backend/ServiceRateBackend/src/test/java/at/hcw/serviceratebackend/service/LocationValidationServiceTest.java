package at.hcw.serviceratebackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationValidationServiceTest {

    // RestTemplate wird gemockt -> es findet KEIN echter HTTP-Call statt
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final LocationValidationService service = new LocationValidationService(restTemplate);

    @Test
    void resolveCityName_returnsPlaceName_forValidZip() {
        var response = new LocationValidationService.ZippopotamResponse(
                "1010",
                List.of(new LocationValidationService.Place("Wien, Innere Stadt"))
        );
        when(restTemplate.getForObject(
                anyString(),
                eq(LocationValidationService.ZippopotamResponse.class),
                anyString()))
                .thenReturn(response);

        String city = service.resolveCityName("1010");

        assertThat(city).isEqualTo("Wien, Innere Stadt");
    }

    @Test
    void resolveCityName_throwsBadRequest_whenZipNotFound() {
        // Zippopotam liefert 404 bei unbekannter PLZ -> RestClientException
        when(restTemplate.getForObject(
                anyString(),
                eq(LocationValidationService.ZippopotamResponse.class),
                anyString()))
                .thenThrow(new RestClientException("404 Not Found"));

        assertThatThrownBy(() -> service.resolveCityName("00000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültige Postleitzahl");
    }

    @Test
    void resolveCityName_throwsBadRequest_whenResponseHasNoPlaces() {
        var emptyResponse = new LocationValidationService.ZippopotamResponse("1010", List.of());
        when(restTemplate.getForObject(
                anyString(),
                eq(LocationValidationService.ZippopotamResponse.class),
                anyString()))
                .thenReturn(emptyResponse);

        assertThatThrownBy(() -> service.resolveCityName("1010"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültige Postleitzahl");
    }
}
