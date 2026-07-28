package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

public record BrowserSourceSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SourceDocumentEntity.Kind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SourceDocumentEntity.Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String language,
        Long characterCount,
        Integer passageCount,
        String errorCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean retryable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version
) {
    public BrowserSourceSummaryDto {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
