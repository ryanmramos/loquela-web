package com.example.whatsanki.service.job;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.export.ExportArtifactRetentionService;
import com.example.whatsanki.service.storage.OriginalDocumentRetentionService;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "whatsanki.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OriginalDocumentRetentionScheduler {

    private final OriginalDocumentRetentionService retentionService;
    private final TwilioMediaCleanupScheduler twilioMediaCleanupScheduler;
    private final WhatsAnkiConfigurationDto configuration;
    private final ExportArtifactRetentionService exportArtifactRetentionService;

    @Scheduled(
            initialDelayString = "${whatsanki.retention.scan-interval:PT1H}",
            fixedDelayString = "${whatsanki.retention.scan-interval:PT1H}"
    )
    public void scheduleExpiredOriginals() {
        retentionService.enqueueExpiredOriginals(Instant.now());
    }

    @Scheduled(
            initialDelayString = "${whatsanki.retention.scan-interval:PT1H}",
            fixedDelayString = "${whatsanki.retention.scan-interval:PT1H}"
    )
    public void schedulePendingTwilioMediaCleanup() {
        twilioMediaCleanupScheduler.enqueuePending(configuration.getRetention().getScanBatchSize());
    }

    @Scheduled(
            initialDelayString = "${whatsanki.retention.scan-interval:PT1H}",
            fixedDelayString = "${whatsanki.retention.scan-interval:PT1H}"
    )
    public void scheduleExpiredExportArtifacts() {
        exportArtifactRetentionService.deleteExpiredArtifacts(Instant.now());
    }
}
