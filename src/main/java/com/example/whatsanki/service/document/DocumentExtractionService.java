package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface DocumentExtractionService {
    void extractAndIndex(UserId userId, SourceId sourceId);
    void markFailed(UserId userId, SourceId sourceId, String errorCode);
}
