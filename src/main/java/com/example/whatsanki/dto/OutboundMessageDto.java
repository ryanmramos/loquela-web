package com.example.whatsanki.dto;

public record OutboundMessageDto(String body) {
    public OutboundMessageDto {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Outbound body must not be blank");
        }
    }
}
