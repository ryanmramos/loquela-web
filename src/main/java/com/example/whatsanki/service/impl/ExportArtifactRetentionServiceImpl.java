package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.service.DeckExportLifecycleService;
import com.example.whatsanki.service.ExportArtifactRetentionService;
import com.example.whatsanki.service.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportArtifactRetentionServiceImpl implements ExportArtifactRetentionService {
    private final ExportRequestRepository exportRequestRepository;
    private final ObjectStore objectStore;
    private final DeckExportLifecycleService lifecycleService;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public int deleteExpiredArtifacts(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        var expired = exportRequestRepository.findAllByStateAndExpiresAtLessThanEqualOrderByExpiresAt(
                ExportRequestEntity.State.READY,
                now,
                PageRequest.of(0, configuration.getRetention().getScanBatchSize())
        );
        int deleted = 0;
        for (ExportRequestEntity export : expired) {
            // Object deletion is outside the database transaction and idempotent. If
            // the subsequent state update fails, the next scan safely repeats it.
            objectStore.delete(export.getArtifactKey());
            if (lifecycleService.markExpiredAfterDeletion(
                    new UserId(export.getUserId()), new ExportRequestId(export.getId()), now
            )) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Deleted expired deck artifacts count={}", deleted);
        }
        return deleted;
    }
}
