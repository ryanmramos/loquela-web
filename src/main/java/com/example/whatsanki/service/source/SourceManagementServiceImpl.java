package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeleteSourceJobPayloadDto;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.dto.SourceSummaryDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.SourceManagementService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceManagementServiceImpl implements SourceManagementService {

    static final int MAX_LIST_LIMIT = 50;

    private final SourceDocumentRepository sourceDocumentRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;

    @Override
    @Transactional(readOnly = true)
    public List<SourceSummaryDto> listRecent(UserId userId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIST_LIMIT);
        }
        List<SourceSummaryDto> sources = sourceDocumentRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId.value(), PageRequest.of(0, limit))
                .stream()
                .map(this::toSummary)
                .toList();
        log.info("Listed sources userId={} resultCount={}", userId.value(), sources.size());
        return sources;
    }

    @Override
    @Transactional
    public SourceDeletionRequestDto requestDeletion(UserId userId, String sourceReference) {
        Objects.requireNonNull(userId, "userId must not be null");
        var sourceIdResult = SourceReferenceCodec.decode(sourceReference);
        if (sourceIdResult.isEmpty()) {
            log.info("Rejected source deletion with invalid reference userId={}", userId.value());
            return SourceDeletionRequestDto.notFoundOrDeleted();
        }

        SourceId sourceId = sourceIdResult.orElseThrow();
        var sourceResult = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (sourceResult.isEmpty()) {
            // Use the same response for invalid, deleted, and another user's source so the
            // command cannot be used to discover whether an external reference exists.
            log.info("Source deletion target was not available userId={} sourceId={}", userId.value(), sourceId.value());
            return SourceDeletionRequestDto.notFoundOrDeleted();
        }

        SourceDocumentEntity source = sourceResult.orElseThrow();
        if (source.getStatus() == SourceDocumentEntity.Status.DELETED) {
            log.info("Source deletion target was already deleted userId={} sourceId={}",
                    userId.value(), sourceId.value());
            return SourceDeletionRequestDto.notFoundOrDeleted();
        }
        if (source.getStatus() == SourceDocumentEntity.Status.DELETING) {
            boolean activeDeletionExists = backgroundJobRepository.existsActiveForSourceAndType(
                    userId.value(),
                    sourceId.value().toString(),
                    BackgroundJobEntity.Type.DELETE_SOURCE.name()
            );
            if (activeDeletionExists) {
                log.info("Source deletion already pending userId={} sourceId={}", userId.value(), sourceId.value());
                return SourceDeletionRequestDto.alreadyPending(sourceId);
            }

            // DELETING without an active job means an earlier worker exhausted its
            // attempts or was administratively cancelled. Re-enqueue from durable source
            // state so deletion is resumable without a database repair.
            JobId replayJobId = enqueueDeletion(userId, sourceId);
            log.info(
                    "Replayed source deletion userId={} sourceId={} jobId={}",
                    userId.value(), sourceId.value(), replayJobId.value()
            );
            return SourceDeletionRequestDto.queued(sourceId, replayJobId);
        }

        source.setStatus(SourceDocumentEntity.Status.DELETING);
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        int cancelledJobs = backgroundJobRepository.cancelPendingForSource(
                userId.value(), sourceId.value().toString(), null, Instant.now()
        );
        JobId jobId = enqueueDeletion(userId, sourceId);
        log.info(
                "Queued source deletion userId={} sourceId={} jobId={} cancelledJobCount={}",
                userId.value(), sourceId.value(), jobId.value(), cancelledJobs
        );
        return SourceDeletionRequestDto.queued(sourceId, jobId);
    }

    private JobId enqueueDeletion(UserId userId, SourceId sourceId) {
        return backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.DELETE_SOURCE,
                DeleteSourceJobPayloadDto.create(sourceId).toMap()
        );
    }

    private SourceSummaryDto toSummary(SourceDocumentEntity source) {
        return new SourceSummaryDto(
                new SourceId(source.getId()),
                SourceReferenceCodec.encode(new SourceId(source.getId())),
                source.getKind(),
                source.getStatus(),
                source.getDisplayName(),
                source.getCharacterCount(),
                source.getPassageCount(),
                source.getErrorCode(),
                source.getCreatedAt()
        );
    }
}
