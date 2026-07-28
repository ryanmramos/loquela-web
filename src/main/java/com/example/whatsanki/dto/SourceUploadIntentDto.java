package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SourceUploadIntentDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String uploadReference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UploadMode uploadMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String completionPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long maxBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> acceptedMediaTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt
) {
    public SourceUploadIntentDto {
        Objects.requireNonNull(uploadReference, "uploadReference must not be null");
        Objects.requireNonNull(uploadMode, "uploadMode must not be null");
        Objects.requireNonNull(completionPath, "completionPath must not be null");
        Objects.requireNonNull(acceptedMediaTypes, "acceptedMediaTypes must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        acceptedMediaTypes = List.copyOf(acceptedMediaTypes);
    }

    public enum UploadMode { APPLICATION }
}
