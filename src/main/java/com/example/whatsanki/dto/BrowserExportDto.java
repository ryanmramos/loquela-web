package com.example.whatsanki.dto;

import com.example.whatsanki.domain.ExportRequestEntity;

import java.time.Instant;

public record BrowserExportDto(
        String reference,
        String deckReference,
        String deckName,
        long cardCount,
        ExportRequestEntity.State state,
        String errorCode,
        Instant createdAt,
        Instant expiresAt,
        boolean downloadAvailable
) { }
