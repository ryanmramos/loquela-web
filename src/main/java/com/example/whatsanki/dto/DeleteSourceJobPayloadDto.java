package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DeleteSourceJobPayloadDto(int schemaVersion, SourceId sourceId) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public DeleteSourceJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported DELETE_SOURCE payload schema version");
        }
        Objects.requireNonNull(sourceId, "sourceId must not be null");
    }

    public static DeleteSourceJobPayloadDto create(SourceId sourceId) {
        return new DeleteSourceJobPayloadDto(CURRENT_SCHEMA_VERSION, sourceId);
    }

    public static DeleteSourceJobPayloadDto from(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (!payload.keySet().equals(java.util.Set.of("schemaVersion", "sourceId"))) {
            throw new IllegalArgumentException("DELETE_SOURCE payload fields are invalid");
        }
        Object version = payload.get("schemaVersion");
        Object sourceId = payload.get("sourceId");
        if (!(version instanceof Number number)
                || Double.compare(number.doubleValue(), CURRENT_SCHEMA_VERSION) != 0) {
            throw new IllegalArgumentException("Unsupported DELETE_SOURCE payload schema version");
        }
        try {
            return new DeleteSourceJobPayloadDto(
                    CURRENT_SCHEMA_VERSION,
                    new SourceId(UUID.fromString(Objects.toString(sourceId)))
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DELETE_SOURCE payload sourceId is invalid", exception);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("sourceId", sourceId.value().toString());
        return Map.copyOf(payload);
    }
}
