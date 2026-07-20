package com.example.whatsanki.dto;

import com.example.whatsanki.domain.WhatsAppAddress;

import java.util.List;
import java.util.Objects;

public record LookupDeliveryPreparationDto(
        WhatsAppAddress recipient,
        List<LookupDeliveryPartDto> parts
) {
    public LookupDeliveryPreparationDto {
        Objects.requireNonNull(recipient, "recipient must not be null");
        if (parts == null || parts.isEmpty() || parts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("parts must contain at least one message");
        }
        parts = List.copyOf(parts);
    }
}
