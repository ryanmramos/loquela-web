package com.example.whatsanki.service.export;

import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeckExportAcceptanceDto;

public interface DeckExportService {
    DeckExportAcceptanceDto accept(UserId userId, String deckName, String normalizedDeckName);
    void execute(UserId userId, ExportRequestId exportRequestId);
    void markFailed(UserId userId, ExportRequestId exportRequestId, String errorCode);
}
