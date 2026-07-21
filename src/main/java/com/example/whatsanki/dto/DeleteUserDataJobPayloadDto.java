package com.example.whatsanki.dto;

import com.example.whatsanki.domain.UserId;

import java.util.Map;
import java.util.UUID;

/** Versioned, ID-only payload for destructive account cleanup work. */
public record DeleteUserDataJobPayloadDto(UserId userId) {
    private static final int SCHEMA_VERSION = 1;

    public static DeleteUserDataJobPayloadDto create(UserId userId) {
        return new DeleteUserDataJobPayloadDto(userId);
    }

    public static DeleteUserDataJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2 || !Integer.valueOf(SCHEMA_VERSION).equals(asInteger(payload.get("schemaVersion")))) {
            throw new IllegalArgumentException("Unsupported delete-user-data job payload");
        }
        Object rawUserId = payload.get("userId");
        if (!(rawUserId instanceof String value)) {
            throw new IllegalArgumentException("Delete-user-data job requires userId");
        }
        try {
            return new DeleteUserDataJobPayloadDto(new UserId(UUID.fromString(value)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Delete-user-data job requires a UUID userId", exception);
        }
    }

    public Map<String, Object> toMap() {
        return Map.of("schemaVersion", SCHEMA_VERSION, "userId", userId.value().toString());
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
