package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface SourceObjectCleanupService {
    void purge(UserId userId, SourceId sourceId);
}
