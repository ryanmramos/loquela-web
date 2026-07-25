package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

import java.util.Map;
import java.util.UUID;

public record FetchVideoSubtitlesJobPayloadDto(SourceId sourceId) {
    private static final int SCHEMA_VERSION = 1;

    public static FetchVideoSubtitlesJobPayloadDto create(SourceId sourceId) {
        return new FetchVideoSubtitlesJobPayloadDto(sourceId);
    }

    public Map<String, Object> toMap() {
        return Map.of("schemaVersion", SCHEMA_VERSION, "sourceId", sourceId.value().toString());
    }

    public static FetchVideoSubtitlesJobPayloadDto from(Map<String, Object> payload) {
        if (payload == null || payload.size() != 2 || !(payload.get("schemaVersion") instanceof Number version)
                || version.intValue() != SCHEMA_VERSION || !(payload.get("sourceId") instanceof String sourceId)) {
            throw new IllegalArgumentException("Invalid fetch-video-subtitles payload");
        }
        try {
            return new FetchVideoSubtitlesJobPayloadDto(new SourceId(UUID.fromString(sourceId)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sourceId must be a UUID", exception);
        }
    }
}
