package com.example.whatsanki.service;

import java.time.Instant;

public interface ExportArtifactRetentionService {
    int deleteExpiredArtifacts(Instant now);
}
