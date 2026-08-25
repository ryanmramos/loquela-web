package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.BackgroundJobStatusDto;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores ID-only work items and applies lease ownership to every state transition. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundJobServiceImpl implements BackgroundJobService {

    private static final int SUPPORTED_PAYLOAD_SCHEMA_VERSION = 1;
    private static final int MAX_STATUS_LIMIT = 50;
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobRetryPolicy retryPolicy;
    private final WhatsAnkiConfigurationDto configuration;

    /** Rejects payloads that could turn the queue into a second content store. */
    @Override
    @Transactional
    public JobId enqueue(
            UserId userId,
            BackgroundJobEntity.Type type,
            Map<String, Object> payload
    ) {
        return enqueue(
                userId,
                type,
                payload,
                configuration.getJobs().getDefaultMaxAttempts(),
                Instant.now()
        );
    }

    @Override
    @Transactional
    public JobId enqueue(
            UserId userId,
            BackgroundJobEntity.Type type,
            Map<String, Object> payload,
            int maxAttempts,
            Instant runAfter
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        BackgroundJobEntity job = new BackgroundJobEntity();
        job.setUserId(userId.value());
        job.setType(type);
        job.setState(BackgroundJobEntity.State.QUEUED);
        job.setPayload(validatePayload(payload));
        job.setAttempts(0);
        job.setMaxAttempts(maxAttempts);
        job.setRunAfter(runAfter == null ? Instant.now() : runAfter);
        BackgroundJobEntity saved = backgroundJobRepository.save(job);

        log.info("Enqueued background job jobId={} userId={} jobType={}", saved.getId(), userId.value(), type);
        return new JobId(saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BackgroundJobStatusDto> findRecent(UserId userId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (limit < 1 || limit > MAX_STATUS_LIMIT) {
            throw new IllegalArgumentException("Status limit must be between 1 and " + MAX_STATUS_LIMIT);
        }
        return backgroundJobRepository.findRecentByUserId(userId.value(), limit).stream()
                .map(this::toStatus)
                .toList();
    }

    @Override
    @Transactional
    public List<ClaimedBackgroundJobDto> claimBatch(
            String workerId,
            Instant now,
            Duration leaseDuration,
            int batchSize
    ) {
        validateWorkerArguments(workerId, now, leaseDuration, batchSize);
        List<ClaimedBackgroundJobDto> claimed = backgroundJobRepository.claimBatch(
                        workerId,
                        now,
                        now.plus(leaseDuration),
                        batchSize
                ).stream()
                .map(this::toClaimed)
                .toList();
        if (!claimed.isEmpty()) {
            log.info("Claimed background jobs workerId={} count={}", workerId, claimed.size());
        }
        return claimed;
    }

    @Override
    @Transactional
    public boolean complete(JobId jobId, String workerId, Instant now) {
        validateLeaseUpdate(jobId, workerId, now);
        boolean updated = backgroundJobRepository.markSucceeded(jobId.value(), workerId, now) == 1;
        logLeaseOutcome("completed", jobId, workerId, updated);
        return updated;
    }

    @Override
    @Transactional
    public boolean fail(
            ClaimedBackgroundJobDto job,
            String workerId,
            BackgroundJobFailureKind failureKind,
            String errorCode,
            Instant now
    ) {
        Objects.requireNonNull(job, "job must not be null");
        Objects.requireNonNull(failureKind, "failureKind must not be null");
        validateLeaseUpdate(job.jobId(), workerId, now);
        validateErrorCode(errorCode);

        boolean terminal = failureKind == BackgroundJobFailureKind.PERMANENT
                || job.attempts() >= job.maxAttempts();
        int updatedRows;
        if (terminal) {
            updatedRows = backgroundJobRepository.markDead(job.jobId().value(), workerId, errorCode, now);
        } else {
            Instant runAfter = now.plus(retryPolicy.retryDelay(job.attempts()));
            updatedRows = backgroundJobRepository.markRetryWaiting(
                    job.jobId().value(), workerId, errorCode, runAfter, now
            );
        }

        boolean updated = updatedRows == 1;
        log.info(
                "Finalized failed background job jobId={} workerId={} terminal={} errorCode={} updated={}",
                job.jobId().value(),
                workerId,
                terminal,
                errorCode,
                updated
        );
        return updated;
    }

    @Override
    @Transactional
    public int recoverExpiredLeases(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        int recovered = backgroundJobRepository.recoverExpiredLeases(now);
        if (recovered > 0) {
            log.warn("Recovered expired background job leases count={}", recovered);
        }
        return recovered;
    }

    private Map<String, Object> validatePayload(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        Object schemaVersion = payload.get("schemaVersion");
        if (!(schemaVersion instanceof Number version)
                || Double.compare(version.doubleValue(), SUPPORTED_PAYLOAD_SCHEMA_VERSION) != 0) {
            throw new IllegalArgumentException("Job payload must use schemaVersion 1");
        }

        // Job payloads are deliberately ID-only. Durable source/lookup/export rows hold
        // content; the queue never becomes an accidental store for text or credentials.
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if ("schemaVersion".equals(entry.getKey())) {
                continue;
            }
            if (!entry.getKey().endsWith("Id") || !isUuid(entry.getValue())) {
                throw new IllegalArgumentException("Job payload fields must be UUID references ending in Id");
            }
        }

        int estimatedBytes = payload.toString().getBytes(StandardCharsets.UTF_8).length;
        if (estimatedBytes > configuration.getJobs().getMaxPayloadBytes()) {
            throw new IllegalArgumentException("Job payload exceeds configured size limit");
        }
        return Map.copyOf(new LinkedHashMap<>(payload));
    }

    private boolean isUuid(Object value) {
        if (value instanceof UUID) {
            return true;
        }
        if (!(value instanceof String stringValue)) {
            return false;
        }
        try {
            UUID.fromString(stringValue);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateWorkerArguments(
            String workerId,
            Instant now,
            Duration leaseDuration,
            int batchSize
    ) {
        validateWorkerId(workerId);
        Objects.requireNonNull(now, "now must not be null");
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    private void validateLeaseUpdate(JobId jobId, String workerId, Instant now) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        validateWorkerId(workerId);
        Objects.requireNonNull(now, "now must not be null");
    }

    private void validateWorkerId(String workerId) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException("workerId must contain 1 to 128 characters");
        }
    }

    private void validateErrorCode(String errorCode) {
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be an uppercase safe diagnostic code");
        }
    }

    private BackgroundJobStatusDto toStatus(BackgroundJobEntity job) {
        return new BackgroundJobStatusDto(
                job.getId(),
                job.getType(),
                job.getState(),
                job.getAttempts(),
                job.getMaxAttempts(),
                job.getLastErrorCode(),
                job.getCreatedAt()
        );
    }

    private ClaimedBackgroundJobDto toClaimed(BackgroundJobEntity job) {
        return new ClaimedBackgroundJobDto(
                new JobId(job.getId()),
                job.getUserId(),
                job.getType(),
                job.getPayload(),
                job.getAttempts(),
                job.getMaxAttempts()
        );
    }

    private void logLeaseOutcome(String outcome, JobId jobId, String workerId, boolean updated) {
        if (updated) {
            log.info("Background job {} jobId={} workerId={}", outcome, jobId.value(), workerId);
        } else {
            log.warn(
                    "Background job lease update rejected outcome={} jobId={} workerId={}",
                    outcome,
                    jobId.value(),
                    workerId
            );
        }
    }
}
