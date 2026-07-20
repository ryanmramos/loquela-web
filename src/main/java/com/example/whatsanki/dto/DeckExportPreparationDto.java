package com.example.whatsanki.dto;

import com.example.whatsanki.domain.ExportRequestId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;

public record DeckExportPreparationDto(
        ExportRequestId exportRequestId,
        String deckName,
        Map<String, Object> snapshot,
        byte[] snapshotSha256,
        boolean artifactReady,
        String artifactKey,
        Instant expiresAt
) {
    public DeckExportPreparationDto {
        snapshot = Map.copyOf(new LinkedHashMap<>(snapshot));
        snapshotSha256 = snapshotSha256.clone();
    }

    @Override
    public byte[] snapshotSha256() { return snapshotSha256.clone(); }
}
