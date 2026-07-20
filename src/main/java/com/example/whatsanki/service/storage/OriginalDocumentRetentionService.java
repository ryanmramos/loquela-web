package com.example.whatsanki.service.storage;

import java.time.Instant;

public interface OriginalDocumentRetentionService {
    int enqueueExpiredOriginals(Instant now);
}
