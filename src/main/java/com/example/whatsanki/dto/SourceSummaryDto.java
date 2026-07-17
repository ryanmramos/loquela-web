package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;

import java.time.Instant;
import java.util.Objects;

public record SourceSummaryDto(
        SourceId sourceId,
        String reference,
        SourceDocumentEntity.Kind kind,
        SourceDocumentEntity.Status status,
        String displayName,
        Long characterCount,
        Integer passageCount,
        String errorCode,
        Instant createdAt
) {
    public SourceSummaryDto {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
