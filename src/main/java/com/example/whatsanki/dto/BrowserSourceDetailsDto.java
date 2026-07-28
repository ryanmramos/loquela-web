package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

public record BrowserSourceDetailsDto(
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
        Long totalWords,
        Long databaseSizeBytes,
        String errorCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean retryable,
        YouTubeDetailsDto youtube,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version
) {
    public BrowserSourceDetailsDto {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public record YouTubeDetailsDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String canonicalUrl,
            String title,
            String channelName,
            Long durationMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String captionKind
    ) {
        public YouTubeDetailsDto {
            Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
            Objects.requireNonNull(captionKind, "captionKind must not be null");
        }
    }
}
