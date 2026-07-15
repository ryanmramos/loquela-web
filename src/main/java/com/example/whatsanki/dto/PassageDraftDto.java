package com.example.whatsanki.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PassageDraftDto(
        String displayText,
        Map<String, Object> location,
        int tokenCount
) {
    public PassageDraftDto {
        if (displayText == null || displayText.isBlank()) {
            throw new IllegalArgumentException("displayText must not be blank");
        }
        Objects.requireNonNull(location, "location must not be null");
        if (tokenCount < 1) {
            throw new IllegalArgumentException("tokenCount must be positive");
        }
        location = Map.copyOf(new LinkedHashMap<>(location));
    }
}
