package com.example.whatsanki.service.export;

import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeckExportPreparationDto;

import java.time.Instant;

public interface DeckExportLifecycleService {
    DeckExportPreparationDto prepare(UserId userId, ExportRequestId exportRequestId);
    Instant markReady(UserId userId, ExportRequestId exportRequestId, String artifactKey, byte[] sha256, Instant now);
    void markFailed(UserId userId, ExportRequestId exportRequestId, String errorCode);
    boolean markExpiredAfterDeletion(UserId userId, ExportRequestId exportRequestId, Instant now);
}
