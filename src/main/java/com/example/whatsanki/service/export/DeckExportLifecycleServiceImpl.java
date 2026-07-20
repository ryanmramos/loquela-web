package com.example.whatsanki.service.export;

import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeckExportPreparationDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.service.exception.DeckExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckExportLifecycleServiceImpl implements DeckExportLifecycleService {
    private final ExportRequestRepository exportRequestRepository;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public DeckExportPreparationDto prepare(UserId userId, ExportRequestId exportRequestId) {
        ExportRequestEntity export = ownedForUpdate(userId, exportRequestId);
        if (export.getState() == ExportRequestEntity.State.FAILED
                || export.getState() == ExportRequestEntity.State.EXPIRED) {
            throw DeckExportException.permanent("EXPORT_REQUEST_NOT_RUNNABLE");
        }
        boolean ready = export.getState() == ExportRequestEntity.State.READY;
        if (!ready) {
            export.setState(ExportRequestEntity.State.RUNNING);
            export.setErrorCode(null);
            exportRequestRepository.save(export);
        }
        Object deckValue = export.getSnapshotPayload().get("deck");
        String deckName = deckValue instanceof Map<?, ?> deck && deck.get("name") instanceof String name
                ? name : "Deck";
        log.info("Prepared deck export userId={} exportRequestId={} alreadyReady={}",
                userId.value(), exportRequestId.value(), ready);
        return new DeckExportPreparationDto(
                exportRequestId, deckName, export.getSnapshotPayload(), export.getSnapshotSha256(),
                ready, export.getArtifactKey(), export.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public Instant markReady(
            UserId userId,
            ExportRequestId exportRequestId,
            String artifactKey,
            byte[] sha256,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        ExportRequestEntity export = ownedForUpdate(userId, exportRequestId);
        if (export.getState() == ExportRequestEntity.State.READY) {
            return export.getExpiresAt();
        }
        if (export.getState() != ExportRequestEntity.State.RUNNING) {
            throw DeckExportException.permanent("EXPORT_STATE_INVALID");
        }
        Instant expiresAt = now.plus(configuration.getRetention().getExportArtifact());
        export.setArtifactKey(artifactKey);
        export.setArtifactSha256(sha256.clone());
        export.setExpiresAt(expiresAt);
        export.setErrorCode(null);
        export.setState(ExportRequestEntity.State.READY);
        exportRequestRepository.save(export);
        log.info("Published deck export userId={} exportRequestId={} expiresAt={}",
                userId.value(), exportRequestId.value(), expiresAt);
        return expiresAt;
    }

    @Override
    @Transactional
    public void markFailed(UserId userId, ExportRequestId exportRequestId, String errorCode) {
        ExportRequestEntity export = ownedForUpdate(userId, exportRequestId);
        // A delivery failure after publication must not discard an otherwise valid
        // artifact. The user can request a fresh link with EXPORT.
        if (export.getState() != ExportRequestEntity.State.READY) {
            export.setState(ExportRequestEntity.State.FAILED);
            export.setErrorCode(errorCode);
            exportRequestRepository.save(export);
        }
        log.warn("Finalized failed deck export userId={} exportRequestId={} errorCode={} artifactReady={}",
                userId.value(), exportRequestId.value(), errorCode,
                export.getState() == ExportRequestEntity.State.READY);
    }

    @Override
    @Transactional
    public boolean markExpiredAfterDeletion(UserId userId, ExportRequestId exportRequestId, Instant now) {
        ExportRequestEntity export = ownedForUpdate(userId, exportRequestId);
        if (export.getState() != ExportRequestEntity.State.READY
                || export.getExpiresAt() == null || export.getExpiresAt().isAfter(now)) {
            return false;
        }
        export.setState(ExportRequestEntity.State.EXPIRED);
        exportRequestRepository.save(export);
        log.info("Marked deck export expired userId={} exportRequestId={}",
                userId.value(), exportRequestId.value());
        return true;
    }

    private ExportRequestEntity ownedForUpdate(UserId userId, ExportRequestId exportRequestId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(exportRequestId, "exportRequestId must not be null");
        return exportRequestRepository.findByIdAndUserIdForUpdate(
                exportRequestId.value(), userId.value()
        ).orElseThrow(() -> DeckExportException.permanent("EXPORT_REQUEST_NOT_FOUND"));
    }
}
