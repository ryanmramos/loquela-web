package com.example.whatsanki.service;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface SourceObjectCleanupLifecycleService {
    boolean prepare(UserId userId, SourceId sourceId);
    void complete(UserId userId, SourceId sourceId);
}
