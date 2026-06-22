package at.hcw.serviceratebackend.dto;

public record PublishDeliveryRequest(
        String deliveryUrl,
        String deliveryLabel,
        Integer expiresInHours
) {}
