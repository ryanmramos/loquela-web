package com.example.whatsanki.service.export;

import java.time.Instant;

public interface ExportArtifactRetentionService {
    int deleteExpiredArtifacts(Instant now);
}
