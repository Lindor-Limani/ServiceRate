package at.hcw.serviceratebackend.dto;

import java.util.UUID;

public record CreateServiceRequest(
        UUID providerId, // Wer bietet das an? (Die ID, die du gerade in der Datenbank gesehen hast!)
        String title,
        String description,
        String category,
        Double price,
        String zipCode   // Österreichische PLZ; wird über Zippopotam.us in einen Ortsnamen aufgelöst
) {}