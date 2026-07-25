package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.SourceDeletionService;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.exception.SourceDeletionException;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupScheduler;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceDeletionServiceImpl implements SourceDeletionService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;
    private final TwilioMediaCleanupScheduler twilioMediaCleanupScheduler;

    @Override
    @Transactional
    public boolean delete(UserId userId, SourceId sourceId, JobId deletionJobId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(deletionJobId, "deletionJobId must not be null");

        var sourceResult = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (sourceResult.isEmpty()) {
            log.info("Source was already deleted userId={} sourceId={}", userId.value(), sourceId.value());
            return false;
        }
        SourceDocumentEntity source = sourceResult.orElseThrow();
        if (source.getStatus() != SourceDocumentEntity.Status.DELETING) {
            throw new SourceDeletionException(BackgroundJobFailureKind.PERMANENT, "INVALID_SOURCE_STATE");
        }
        long runningJobs = backgroundJobRepository.countRunningForSource(
                userId.value(), sourceId.value().toString(), deletionJobId.value()
        );
        if (runningJobs > 0) {
            throw new SourceDeletionException(BackgroundJobFailureKind.TRANSIENT, "SOURCE_BUSY");
        }

        String objectKey = source.getObjectKey();
        if (objectKey == null && isObjectBacked(source.getKind())) {
            objectKey = objectKeyFactory.originalSource(userId, sourceId);
        }
        if (objectKey != null) {
            try {
                // DeleteObject is idempotent. If the database transaction later rolls
                // back, retrying this job safely repeats the object deletion first.
                objectStore.delete(objectKey);
            } catch (ObjectStoreException exception) {
                throw new SourceDeletionException(
                        BackgroundJobFailureKind.TRANSIENT,
                        "SOURCE_OBJECT_DELETE_FAILED",
                        exception
                );
            }
        }

        int cancelledJobs = backgroundJobRepository.cancelPendingForSource(
                userId.value(), sourceId.value().toString(), deletionJobId.value(), Instant.now()
        );

        // A source can be deleted while its ADD job is still queued. Remove encrypted
        // staging as part of the same transaction so deletion never leaves copied text
        // behind merely because passage creation did not finish.
        if (source.getInboundMessageId() != null) {
            inboundMessageRepository.findByIdAndUserId(source.getInboundMessageId(), userId.value())
                    .ifPresent(inboundMessage -> {
                        if (inboundMessage.getTwilioMediaSid() != null) {
                            twilioMediaCleanupScheduler.enqueueIfNeeded(
                                    userId, new com.example.whatsanki.domain.InboundMessageId(inboundMessage.getId())
                            );
                        }
                        inboundMessage.setBodyCiphertext(null);
                        inboundMessage.setMediaUrlCiphertext(null);
                        inboundMessage.setMediaContentType(null);
                        inboundMessageRepository.save(inboundMessage);
                    });
        }
        sourceDocumentRepository.delete(source);
        log.info(
                "Deleted source userId={} sourceId={} cancelledJobCount={}",
                userId.value(), sourceId.value(), cancelledJobs
        );
        return true;
    }

    private boolean isObjectBacked(SourceDocumentEntity.Kind kind) {
        return kind == SourceDocumentEntity.Kind.PDF
                || kind == SourceDocumentEntity.Kind.TXT
                || kind == SourceDocumentEntity.Kind.EPUB;
    }
}
