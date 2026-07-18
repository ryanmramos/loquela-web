package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentAcceptanceDto;
import com.example.whatsanki.dto.TwilioMediaMetadataDto;

public interface DocumentIngestionService {
    DocumentAcceptanceDto accept(
            UserId userId,
            InboundMessageId inboundMessageId,
            TwilioMediaMetadataDto media
    );

    void fetchAndStore(UserId userId, SourceId sourceId);

    void markFailed(UserId userId, SourceId sourceId, String errorCode, boolean clearStaging);
}
