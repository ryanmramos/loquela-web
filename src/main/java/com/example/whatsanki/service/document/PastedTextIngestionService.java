package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;

public interface PastedTextIngestionService {
    PastedTextAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String text);

    void ingest(UserId userId, SourceId sourceId);

    void markFailed(UserId userId, SourceId sourceId, String errorCode);
}
