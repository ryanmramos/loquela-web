package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

public record ActivityItemDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Category category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Operation operation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Lifecycle lifecycle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
        String resourceLabel,
        DeliveryStatus deliveryStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        String target
) {
    public ActivityItemDto {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public enum Category { SOURCE, LOOKUP, EXPORT }
    public enum Operation { INGEST_SOURCE, DELETE_SOURCE, LOOKUP, EXPORT_DECK }
    public enum Lifecycle { ACTIVE, COMPLETED, FAILED }
    public enum Status {
        RECEIVED, FETCHING, STORED, PARSING, NORMALIZING, EXTRACTING, INDEXING, READY, FAILED, DELETING, DELETED,
        PENDING, EXPIRED, QUEUED, RUNNING
    }
    public enum DeliveryStatus { NOT_APPLICABLE, PENDING, PROVIDER_ACCEPTED, FAILED }
}
