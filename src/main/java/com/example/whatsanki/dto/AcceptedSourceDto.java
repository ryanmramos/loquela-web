package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

public record AcceptedSourceDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SourceDocumentEntity.Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean existing
) {
    public AcceptedSourceDto {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
