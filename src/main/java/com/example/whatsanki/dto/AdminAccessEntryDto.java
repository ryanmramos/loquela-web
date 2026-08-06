package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminAccessEntryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminAccessRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminAccessStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        UUID createdByUserId,
        UUID updatedByUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
}
