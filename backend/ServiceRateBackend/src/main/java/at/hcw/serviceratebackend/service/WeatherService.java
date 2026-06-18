package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.WeatherCurrentResponse;
import at.hcw.serviceratebackend.dto.WeatherForecastResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final String CURRENT_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openweather.api-key:}")
    private String apiKey;

    public WeatherCurrentResponse current(String city) {
        requireApiKey();
        JsonNode root = get(CURRENT_URL, city);
        JsonNode weather = root.path("weather").path(0);

        return new WeatherCurrentResponse(
                Math.round((float) root.path("main").path("temp").asDouble()),
                weather.path("description").asText(""),
                weather.path("main").asText(""),
                root.path("name").asText(city)
        );
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public WeatherForecastResponse forecast(String city, LocalDate date) {
        requireApiKey();
        JsonNode root = get(FORECAST_URL, city);

        JsonNode closest = root.path("list").findValuesAsText("dt_txt").isEmpty()
                ? null
                : findClosestForecastBlock(root.path("list"), date);

        if (closest == null) {
            throw new IllegalArgumentException("Keine Wettervorhersage für dieses Datum verfügbar.");
        }

        JsonNode weather = closest.path("weather").path(0);
        return new WeatherForecastResponse(
                Math.round((float) closest.path("main").path("temp").asDouble()),
                weather.path("description").asText(""),
                weather.path("main").asText(""),
                date.toString()
        );
    }

    private JsonNode get(String baseUrl, String city) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("q", city)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric")
                    .queryParam("lang", "de")
                    .toUriString();

            String json = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Wetterdaten konnten nicht geladen werden.", e);
        }
    }

    private JsonNode findClosestForecastBlock(JsonNode list, LocalDate date) {
        LocalDateTime noon = date.atTime(12, 0);

        return list.findParents("dt_txt").stream()
                .filter(block -> block.path("dt_txt").asText("").startsWith(date.toString()))
                .min(Comparator.comparingLong(block -> Math.abs(ChronoUnit.MINUTES.between(
                        LocalDateTime.parse(block.path("dt_txt").asText().replace(" ", "T")),
                        noon
                ))))
                .orElse(null);
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENWEATHER_API_KEY ist nicht konfiguriert.");
        }
    }
}
