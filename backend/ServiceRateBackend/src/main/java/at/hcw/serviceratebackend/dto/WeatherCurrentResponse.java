package at.hcw.serviceratebackend.dto;

public record WeatherCurrentResponse(
        int temperature,
        String description,
        String main,
        String city
) {}
