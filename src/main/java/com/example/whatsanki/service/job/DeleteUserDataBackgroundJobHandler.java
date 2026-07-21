package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeleteUserDataJobPayloadDto;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.exception.AccountDeletionException;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteUserDataBackgroundJobHandler implements BackgroundJobHandler {

    private final AccountDeletionService accountDeletionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.DELETE_USER_DATA;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        DeleteUserDataJobPayloadDto payload = parsePayload(job);
        if (!payload.userId().value().equals(job.userId())) {
            throw invalidPayload(null);
        }
        try {
            accountDeletionService.delete(payload.userId(), job.jobId());
            log.info("Processed DELETE_USER_DATA job jobId={} userId={}", job.jobId().value(), payload.userId().value());
        } catch (AccountDeletionException exception) {
            log.warn(
                    "Account deletion deferred or rejected jobId={} userId={} failureKind={} errorCode={}",
                    job.jobId().value(), payload.userId().value(), exception.failureKind(), exception.errorCode()
            );
            throw new BackgroundJobExecutionException(exception.failureKind(), exception.errorCode(), exception);
        }
    }

    private DeleteUserDataJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.DELETE_USER_DATA || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return DeleteUserDataJobPayloadDto.from(job.payload());
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
