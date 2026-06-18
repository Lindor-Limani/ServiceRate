package at.hcw.serviceratebackend.dto;

public record WeatherForecastResponse(
        int temperature,
        String description,
        String main,
        String date
) {}
