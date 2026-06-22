package at.hcw.serviceratebackend.dto;

public record RecordPaymentRequest(
        String provider,
        String note
) {
}
