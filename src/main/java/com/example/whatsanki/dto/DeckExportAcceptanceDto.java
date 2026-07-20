package com.example.whatsanki.dto;

import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.JobId;

public record DeckExportAcceptanceDto(
        ExportRequestId exportRequestId,
        JobId jobId,
        String deckName,
        int cardCount
) { }
