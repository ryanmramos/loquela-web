package com.example.whatsanki.service;

import java.time.Instant;

public interface OriginalDocumentRetentionService {
    int enqueueExpiredOriginals(Instant now);
}
