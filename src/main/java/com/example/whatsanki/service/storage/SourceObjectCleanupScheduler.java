package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface SourceObjectCleanupScheduler {
    void enqueueIfNeeded(UserId userId, SourceId sourceId);
}
