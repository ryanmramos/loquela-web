package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.Objects;

public record PastedTextAcceptanceDto(
        SourceId sourceId,
        int characterCount
) {
    public PastedTextAcceptanceDto {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (characterCount < 1) {
            throw new IllegalArgumentException("characterCount must be positive");
        }
    }
}
