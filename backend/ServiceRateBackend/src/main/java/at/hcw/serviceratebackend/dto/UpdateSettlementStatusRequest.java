package at.hcw.serviceratebackend.dto;

public record UpdateSettlementStatusRequest(
        String status,
        String note
) {}
