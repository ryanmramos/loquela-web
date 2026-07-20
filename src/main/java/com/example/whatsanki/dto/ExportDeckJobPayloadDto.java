package com.example.whatsanki.dto;

import com.example.whatsanki.domain.ExportRequestId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small, versioned queue message; card content remains in the export snapshot. */
public record ExportDeckJobPayloadDto(int schemaVersion, ExportRequestId exportRequestId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ExportDeckJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || exportRequestId == null) {
            throw new IllegalArgumentException("Invalid export-deck job payload");
        }
    }

    public static ExportDeckJobPayloadDto create(ExportRequestId exportRequestId) {
        return new ExportDeckJobPayloadDto(CURRENT_SCHEMA_VERSION, exportRequestId);
    }

    public static ExportDeckJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2) {
            throw new IllegalArgumentException("Invalid export-deck job payload");
        }
        Object version = payload.get("schemaVersion");
        Object id = payload.get("exportRequestId");
        if (!(version instanceof Number number) || number.intValue() != CURRENT_SCHEMA_VERSION
                || !(id instanceof String || id instanceof UUID)) {
            throw new IllegalArgumentException("Invalid export-deck job payload");
        }
        try {
            UUID uuid = id instanceof UUID value ? value : UUID.fromString((String) id);
            return create(new ExportRequestId(uuid));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid export-deck job payload", exception);
        }
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("exportRequestId", exportRequestId.value().toString());
        return Map.copyOf(payload);
    }
}
