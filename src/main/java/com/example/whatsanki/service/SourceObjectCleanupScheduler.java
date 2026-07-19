package com.example.whatsanki.service;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface SourceObjectCleanupScheduler {
    void enqueueIfNeeded(UserId userId, SourceId sourceId);
}
