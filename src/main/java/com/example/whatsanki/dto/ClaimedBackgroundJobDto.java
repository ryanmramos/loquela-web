package com.example.whatsanki.dto;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ClaimedBackgroundJobDto(
        JobId jobId,
        UUID userId,
        BackgroundJobEntity.Type type,
        Map<String, Object> payload,
        int attempts,
        int maxAttempts
) {
    public ClaimedBackgroundJobDto {
        payload = Map.copyOf(new LinkedHashMap<>(payload));
    }
}
