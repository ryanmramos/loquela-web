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
        TimedTranscriptDetailsDto timedTranscript,
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

    public record TimedTranscriptDetailsDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String format,
            String originalFilename,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int cueCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long normalizedCharacterCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long timelineEndMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String parserVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String normalizerVersion,
            String originUrl, String originProvider, String originProviderId, Long timelineOffsetMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ignoredStructuralBlockCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int strippedMarkupCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int emptyCueCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int coalescedCueCount
    ) {
        public TimedTranscriptDetailsDto {
            Objects.requireNonNull(format, "format must not be null");
            Objects.requireNonNull(parserVersion, "parserVersion must not be null");
            Objects.requireNonNull(normalizerVersion, "normalizerVersion must not be null");
        }
    }
}
