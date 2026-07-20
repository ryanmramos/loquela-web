package com.example.whatsanki.dto;

import com.example.whatsanki.domain.OutboundMessageId;

import java.util.Objects;

public record LookupDeliveryPartDto(
        OutboundMessageId outboundMessageId,
        int partNumber,
        String body,
        boolean providerAccepted
) {
    public LookupDeliveryPartDto {
        Objects.requireNonNull(outboundMessageId, "outboundMessageId must not be null");
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber must be positive");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
    }
}
