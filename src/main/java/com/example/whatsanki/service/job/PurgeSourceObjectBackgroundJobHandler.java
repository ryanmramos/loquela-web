package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.PurgeSourceObjectJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.SourceObjectCleanupException;
import com.example.whatsanki.service.storage.SourceObjectCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PurgeSourceObjectBackgroundJobHandler implements BackgroundJobHandler {

    private final SourceObjectCleanupService cleanupService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.PURGE_SOURCE_OBJECT;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        PurgeSourceObjectJobPayloadDto payload = parsePayload(job);
        try {
            cleanupService.purge(new UserId(job.userId()), payload.sourceId());
        } catch (SourceObjectCleanupException exception) {
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        }
    }

    private PurgeSourceObjectJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.PURGE_SOURCE_OBJECT || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return PurgeSourceObjectJobPayloadDto.from(job.payload());
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
