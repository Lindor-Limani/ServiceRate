package at.hcw.serviceratebackend.dto;

public record PayPalCaptureRequest(
        String orderId
) {}
