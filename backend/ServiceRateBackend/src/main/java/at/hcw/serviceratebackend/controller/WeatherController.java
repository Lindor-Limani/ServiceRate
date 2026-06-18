package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.WeatherCurrentResponse;
import at.hcw.serviceratebackend.dto.WeatherForecastResponse;
import at.hcw.serviceratebackend.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/current")
    public WeatherCurrentResponse current(@RequestParam(defaultValue = "Vienna") String city) {
        return weatherService.current(city);
    }

    @GetMapping("/forecast")
    public WeatherForecastResponse forecast(
            @RequestParam(defaultValue = "Vienna") String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return weatherService.forecast(city, date);
    }
}
