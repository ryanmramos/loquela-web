package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FetchMediaJobPayloadDto(int schemaVersion, SourceId sourceId) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public FetchMediaJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported FETCH_MEDIA payload schema version");
        }
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }

    public static FetchMediaJobPayloadDto create(SourceId sourceId) {
        return new FetchMediaJobPayloadDto(CURRENT_SCHEMA_VERSION, sourceId);
    }

    public static FetchMediaJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2) {
            throw new IllegalArgumentException("FETCH_MEDIA payload must contain schemaVersion and sourceId");
        }
        Object versionValue = payload.get("schemaVersion");
        if (!(versionValue instanceof Number version)
                || Double.compare(version.doubleValue(), CURRENT_SCHEMA_VERSION) != 0) {
            throw new IllegalArgumentException("Unsupported FETCH_MEDIA payload schema version");
        }
        try {
            return create(new SourceId(UUID.fromString(String.valueOf(payload.get("sourceId")))));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("FETCH_MEDIA sourceId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        return Map.of("schemaVersion", schemaVersion, "sourceId", sourceId.value().toString());
    }
}
