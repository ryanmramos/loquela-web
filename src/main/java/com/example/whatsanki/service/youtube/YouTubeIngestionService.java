package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface YouTubeIngestionService {
    void fetchAndIndex(UserId userId, SourceId sourceId);
    void markFailed(UserId userId, SourceId sourceId, String errorCode);
}
