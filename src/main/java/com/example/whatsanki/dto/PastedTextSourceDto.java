package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

public record PastedTextSourceDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sourceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int characterCount
) {
    public PastedTextSourceDto {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (characterCount < 1) {
            throw new IllegalArgumentException("characterCount must be positive");
        }
    }

    public enum Status {
        RECEIVED
    }
}
