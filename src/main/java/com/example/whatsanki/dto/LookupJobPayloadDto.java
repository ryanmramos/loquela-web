package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LookupJobPayloadDto(int schemaVersion, LookupSessionId lookupSessionId) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public LookupJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported LOOKUP payload schema version");
        }
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
    }

    public static LookupJobPayloadDto create(LookupSessionId lookupSessionId) {
        return new LookupJobPayloadDto(CURRENT_SCHEMA_VERSION, lookupSessionId);
    }

    public static LookupJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2) {
            throw new IllegalArgumentException("LOOKUP payload must contain schemaVersion and lookupSessionId");
        }
        Object versionValue = payload.get("schemaVersion");
        if (!(versionValue instanceof Number version)
                || Double.compare(version.doubleValue(), CURRENT_SCHEMA_VERSION) != 0) {
            throw new IllegalArgumentException("Unsupported LOOKUP payload schema version");
        }
        try {
            return new LookupJobPayloadDto(
                    CURRENT_SCHEMA_VERSION,
                    new LookupSessionId(UUID.fromString(String.valueOf(payload.get("lookupSessionId"))))
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LOOKUP lookupSessionId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "schemaVersion", schemaVersion,
                "lookupSessionId", lookupSessionId.value().toString()
        );
    }
}
