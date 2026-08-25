package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.BackgroundJobStatusDto;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Enqueues jobs and owns the database-backed lease transitions used by workers. */
public interface BackgroundJobService {
    JobId enqueue(UserId userId, BackgroundJobEntity.Type type, Map<String, Object> payload);

    JobId enqueue(
            UserId userId,
            BackgroundJobEntity.Type type,
            Map<String, Object> payload,
            int maxAttempts,
            Instant runAfter
    );

    List<BackgroundJobStatusDto> findRecent(UserId userId, int limit);

    List<ClaimedBackgroundJobDto> claimBatch(
            String workerId,
            Instant now,
            Duration leaseDuration,
            int batchSize
    );

    boolean complete(JobId jobId, String workerId, Instant now);

    boolean fail(
            ClaimedBackgroundJobDto job,
            String workerId,
            BackgroundJobFailureKind failureKind,
            String errorCode,
            Instant now
    );

    int recoverExpiredLeases(Instant now);
}
