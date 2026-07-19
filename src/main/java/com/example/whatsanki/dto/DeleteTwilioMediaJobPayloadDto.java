package com.example.whatsanki.dto;

import com.example.whatsanki.domain.InboundMessageId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DeleteTwilioMediaJobPayloadDto(int schemaVersion, InboundMessageId inboundMessageId) {
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public DeleteTwilioMediaJobPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported DELETE_TWILIO_MEDIA payload schema version");
        }
        if (inboundMessageId == null) {
            throw new IllegalArgumentException("DELETE_TWILIO_MEDIA inboundMessageId is required");
        }
    }

    public static DeleteTwilioMediaJobPayloadDto create(InboundMessageId inboundMessageId) {
        return new DeleteTwilioMediaJobPayloadDto(CURRENT_SCHEMA_VERSION, inboundMessageId);
    }

    public static DeleteTwilioMediaJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2
                || !payload.containsKey("schemaVersion") || !payload.containsKey("inboundMessageId")) {
            throw new IllegalArgumentException(
                    "DELETE_TWILIO_MEDIA payload must contain schemaVersion and inboundMessageId"
            );
        }
        Object version = payload.get("schemaVersion");
        if (!(version instanceof Number number) || number.intValue() != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported DELETE_TWILIO_MEDIA payload schema version");
        }
        try {
            return create(new InboundMessageId(UUID.fromString(payload.get("inboundMessageId").toString())));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DELETE_TWILIO_MEDIA inboundMessageId must be a UUID", exception);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("inboundMessageId", inboundMessageId.value().toString());
        return Map.copyOf(payload);
    }
}
