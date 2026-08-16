package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface TimedTranscriptIngestionService {
    void ingest(UserId userId, SourceId sourceId);
    void markFailedAndDelete(UserId userId, SourceId sourceId, String errorCode);
}
