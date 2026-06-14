package at.hcw.serviceratebackend.dto;

public record UpdateServiceRequest(
        String title,
        String description,
        String category,
        Double price
) {}
