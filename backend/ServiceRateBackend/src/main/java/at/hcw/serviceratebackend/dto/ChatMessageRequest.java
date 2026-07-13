package at.hcw.serviceratebackend.dto;

public record ChatMessageRequest(
        String content,
        String imageDataUrl,
        String imageName
) {}
