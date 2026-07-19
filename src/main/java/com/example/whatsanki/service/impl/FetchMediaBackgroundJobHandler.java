package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.FetchMediaJobPayloadDto;
import com.example.whatsanki.service.BackgroundJobHandler;
import com.example.whatsanki.service.DocumentIngestionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FetchMediaBackgroundJobHandler implements BackgroundJobHandler {

    private final DocumentIngestionService documentIngestionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.FETCH_MEDIA;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        FetchMediaJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            documentIngestionService.fetchAndStore(userId, payload.sourceId());
            log.info("Processed FETCH_MEDIA job jobId={} userId={} sourceId={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value());
        } catch (MediaAcquisitionException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) {
                try {
                    documentIngestionService.markFailed(
                            userId, payload.sourceId(), exception.errorCode(), true
                    );
                } catch (MediaAcquisitionException cleanupException) {
                    // Cleanup must finish before a source is terminal. Reclassify a
                    // cleanup outage as transient even when the original input was bad.
                    throw new BackgroundJobExecutionException(
                            BackgroundJobFailureKind.TRANSIENT,
                            cleanupException.errorCode(),
                            cleanupException
                    );
                }
            }
            log.warn("Media acquisition failed jobId={} userId={} sourceId={} failureKind={} errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(),
                    exception.failureKind(), exception.errorCode(), terminal);
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                documentIngestionService.markFailed(
                        userId, payload.sourceId(), "MEDIA_ACQUISITION_FAILED", true
                );
            }
            log.error(
                    "Unexpected media acquisition failure jobId={} userId={} sourceId={} attempt={} maxAttempts={} exceptionType={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(),
                    job.attempts(), job.maxAttempts(), exception.getClass().getSimpleName()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT,
                    "MEDIA_ACQUISITION_FAILED",
                    exception
            );
        }
    }

    private FetchMediaJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.FETCH_MEDIA || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return FetchMediaJobPayloadDto.from(job.payload());
        } catch (IllegalArgumentException exception) {
            throw invalidPayload(exception);
        }
    }

    private BackgroundJobExecutionException invalidPayload(Throwable cause) {
        return new BackgroundJobExecutionException(
                BackgroundJobFailureKind.PERMANENT, "INVALID_JOB_PAYLOAD", cause
        );
    }
}
