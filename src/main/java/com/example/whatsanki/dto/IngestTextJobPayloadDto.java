package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IngestTextJobPayloadDto(int schemaVersion, SourceId sourceId) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public IngestTextJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported INGEST_TEXT payload schema version");
        }
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }

    public static IngestTextJobPayloadDto create(SourceId sourceId) {
        return new IngestTextJobPayloadDto(CURRENT_SCHEMA_VERSION, sourceId);
    }

    public static IngestTextJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2) {
            throw new IllegalArgumentException("INGEST_TEXT payload must contain schemaVersion and sourceId");
        }

        Object versionValue = payload.get("schemaVersion");
        if (!(versionValue instanceof Number version)
                || Double.compare(version.doubleValue(), CURRENT_SCHEMA_VERSION) != 0) {
            throw new IllegalArgumentException("Unsupported INGEST_TEXT payload schema version");
        }

        Object sourceIdValue = payload.get("sourceId");
        try {
            return new IngestTextJobPayloadDto(
                    CURRENT_SCHEMA_VERSION,
                    new SourceId(UUID.fromString(String.valueOf(sourceIdValue)))
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("INGEST_TEXT sourceId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "schemaVersion", schemaVersion,
                "sourceId", sourceId.value().toString()
        );
    }
}
