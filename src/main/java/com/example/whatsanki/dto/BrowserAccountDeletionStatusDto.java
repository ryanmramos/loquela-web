package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record BrowserAccountDeletionStatusDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        String supportCode
) {
    public enum Status { QUEUED, IN_PROGRESS, NEEDS_ATTENTION }
}
