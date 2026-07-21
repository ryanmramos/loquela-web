package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.exception.AccountDeletionException;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps database transactions short around the external object-store deletion step. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionCleanupService {

    private final AppUserRepository appUserRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ExportRequestRepository exportRequestRepository;
    private final ObjectKeyFactory objectKeyFactory;

    @Transactional(readOnly = true)
    public Set<String> collectObjectKeys(UserId userId, JobId deletionJobId) {
        var user = appUserRepository.findById(userId.value());
        if (user.isEmpty()) {
            // A prior successful worker may have deleted the user before its lease was
            // acknowledged. Treat the recovered job as an idempotent success.
            return Set.of();
        }
        if (user.orElseThrow().getStatus() != AppUserEntity.Status.DELETING) {
            throw new AccountDeletionException(
                    com.example.whatsanki.domain.BackgroundJobFailureKind.PERMANENT,
                    "INVALID_USER_DELETION_STATE"
            );
        }
        ensureNoOtherRunningJobs(userId, deletionJobId);

        Set<String> keys = new LinkedHashSet<>();
        sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()).forEach(source -> {
            if (source.getObjectKey() != null) {
                keys.add(source.getObjectKey());
            } else if (source.getKind() != SourceDocumentEntity.Kind.PASTED_TEXT) {
                // Object keys are deterministic, so this also covers a worker that put
                // an object but crashed before it could persist its metadata.
                keys.add(objectKeyFactory.originalSource(userId, new SourceId(source.getId())));
            }
        });
        exportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()).forEach(export -> {
            if (export.getArtifactKey() != null) {
                keys.add(export.getArtifactKey());
            }
            // The exporter writes to a deterministic key. Deleting it even when the
            // row is not READY prevents a crash between upload and DB publication from
            // orphaning an Anki artifact.
            keys.add(objectKeyFactory.exportedDeck(userId, new ExportRequestId(export.getId())));
        });
        return Set.copyOf(keys);
    }

    @Transactional
    public void deleteUserAfterObjects(UserId userId, JobId deletionJobId) {
        var user = appUserRepository.findById(userId.value());
        if (user.isEmpty()) {
            return;
        }
        if (user.orElseThrow().getStatus() != AppUserEntity.Status.DELETING) {
            throw new AccountDeletionException(
                    com.example.whatsanki.domain.BackgroundJobFailureKind.PERMANENT,
                    "INVALID_USER_DELETION_STATE"
            );
        }
        ensureNoOtherRunningJobs(userId, deletionJobId);

        // Database foreign keys cascade all user-owned rows, including encrypted inbound
        // content, sources/passages, jobs, lookups/outbox, decks/cards, exports, and the
        // hashed confirmation request. Object keys were deleted before this transaction.
        appUserRepository.delete(user.orElseThrow());
        log.info("Deleted account database rows userId={}", userId.value());
    }

    private void ensureNoOtherRunningJobs(UserId userId, JobId deletionJobId) {
        if (backgroundJobRepository.countRunningForUser(userId.value(), deletionJobId.value()) > 0) {
            throw new AccountDeletionException(
                    com.example.whatsanki.domain.BackgroundJobFailureKind.TRANSIENT,
                    "USER_DATA_BUSY"
            );
        }
    }
}
