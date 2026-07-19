package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PurgeSourceObjectJobPayloadDto(int schemaVersion, SourceId sourceId) {
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public PurgeSourceObjectJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported PURGE_SOURCE_OBJECT payload schema version");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("PURGE_SOURCE_OBJECT sourceId is required");
        }
    }

    public static PurgeSourceObjectJobPayloadDto create(SourceId sourceId) {
        return new PurgeSourceObjectJobPayloadDto(CURRENT_SCHEMA_VERSION, sourceId);
    }

    public static PurgeSourceObjectJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2
                || !payload.containsKey("schemaVersion") || !payload.containsKey("sourceId")) {
            throw new IllegalArgumentException("PURGE_SOURCE_OBJECT payload must contain schemaVersion and sourceId");
        }
        Object version = payload.get("schemaVersion");
        if (!(version instanceof Number number) || number.intValue() != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported PURGE_SOURCE_OBJECT payload schema version");
        }
        try {
            return create(new SourceId(UUID.fromString(payload.get("sourceId").toString())));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("PURGE_SOURCE_OBJECT sourceId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("sourceId", sourceId.value().toString());
        return Map.copyOf(payload);
    }
}
