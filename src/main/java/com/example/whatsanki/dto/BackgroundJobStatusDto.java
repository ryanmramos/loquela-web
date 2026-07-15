package com.example.whatsanki.dto;

import com.example.whatsanki.domain.BackgroundJobEntity;

import java.time.Instant;
import java.util.UUID;

public record BackgroundJobStatusDto(
        UUID id,
        BackgroundJobEntity.Type type,
        BackgroundJobEntity.State state,
        int attempts,
        int maxAttempts,
        String lastErrorCode,
        Instant createdAt
) {
}
