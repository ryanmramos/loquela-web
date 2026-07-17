package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.service.BackgroundJobHandler;
import com.example.whatsanki.service.PastedTextIngestionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.PastedTextIngestionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestTextBackgroundJobHandler implements BackgroundJobHandler {

    private final PastedTextIngestionService pastedTextIngestionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.INGEST_TEXT;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        IngestTextJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            pastedTextIngestionService.ingest(userId, payload.sourceId());
            log.info(
                    "Processed INGEST_TEXT job jobId={} userId={} sourceId={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value()
            );
        } catch (PastedTextIngestionException exception) {
            // Deterministic source/input failures will not improve on retry. Persist the
            // source failure separately, then let the generic runner dead-letter the job.
            pastedTextIngestionService.markFailed(userId, payload.sourceId(), exception.errorCode());
            log.warn(
                    "Pasted-text ingestion rejected jobId={} userId={} sourceId={} errorCode={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(), exception.errorCode()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.PERMANENT,
                    exception.errorCode(),
                    exception
            );
        } catch (RuntimeException exception) {
            // Infrastructure and other unexpected failures remain retryable. On the
            // final claimed attempt, also close the source lifecycle before the queue
            // moves the job to DEAD.
            if (job.attempts() >= job.maxAttempts()) {
                pastedTextIngestionService.markFailed(userId, payload.sourceId(), "INGEST_TEXT_FAILED");
            }
            log.error(
                    "Unexpected pasted-text ingestion failure jobId={} userId={} sourceId={} attempt={} maxAttempts={} exceptionType={}",
                    job.jobId().value(),
                    userId.value(),
                    payload.sourceId().value(),
                    job.attempts(),
                    job.maxAttempts(),
                    exception.getClass().getSimpleName()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT,
                    "INGEST_TEXT_FAILED",
                    exception
            );
        }
    }

    private IngestTextJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.INGEST_TEXT || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return IngestTextJobPayloadDto.from(job.payload());
        } catch (IllegalArgumentException exception) {
            throw invalidPayload(exception);
        }
    }

    private BackgroundJobExecutionException invalidPayload(Throwable cause) {
        return new BackgroundJobExecutionException(
                BackgroundJobFailureKind.PERMANENT,
                "INVALID_JOB_PAYLOAD",
                cause
        );
    }
}
