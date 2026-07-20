package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeleteSourceJobPayloadDto;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import com.example.whatsanki.service.SourceDeletionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.SourceDeletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSourceBackgroundJobHandler implements BackgroundJobHandler {

    private final SourceDeletionService sourceDeletionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.DELETE_SOURCE;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        DeleteSourceJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            boolean deleted = sourceDeletionService.delete(
                    userId, payload.sourceId(), job.jobId()
            );
            log.info(
                    "Processed DELETE_SOURCE job jobId={} userId={} sourceId={} deleted={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(), deleted
            );
        } catch (SourceDeletionException exception) {
            log.warn(
                    "Source deletion deferred or rejected jobId={} userId={} sourceId={} failureKind={} errorCode={}",
                    job.jobId().value(),
                    userId.value(),
                    payload.sourceId().value(),
                    exception.failureKind(),
                    exception.errorCode()
            );
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        }
    }

    private DeleteSourceJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.DELETE_SOURCE || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return DeleteSourceJobPayloadDto.from(job.payload());
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
