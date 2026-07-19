package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ExtractDocumentJobPayloadDto(int schemaVersion, SourceId sourceId) {
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public ExtractDocumentJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported EXTRACT_DOCUMENT payload schema version");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("EXTRACT_DOCUMENT sourceId is required");
        }
    }

    public static ExtractDocumentJobPayloadDto create(SourceId sourceId) {
        return new ExtractDocumentJobPayloadDto(CURRENT_SCHEMA_VERSION, sourceId);
    }

    public static ExtractDocumentJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2
                || !payload.containsKey("schemaVersion") || !payload.containsKey("sourceId")) {
            throw new IllegalArgumentException("EXTRACT_DOCUMENT payload must contain schemaVersion and sourceId");
        }
        Object version = payload.get("schemaVersion");
        if (!(version instanceof Number number) || number.intValue() != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported EXTRACT_DOCUMENT payload schema version");
        }
        try {
            return create(new SourceId(UUID.fromString(payload.get("sourceId").toString())));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("EXTRACT_DOCUMENT sourceId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("sourceId", sourceId.value().toString());
        return Map.copyOf(payload);
    }
}
