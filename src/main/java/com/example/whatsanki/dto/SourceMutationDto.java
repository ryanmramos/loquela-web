package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

public record SourceMutationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SourceDocumentEntity.Status status,
        String jobReference
) {
    public SourceMutationDto {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
