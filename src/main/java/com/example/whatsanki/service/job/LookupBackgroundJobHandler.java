package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.LookupJobPayloadDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import com.example.whatsanki.service.lookup.LookupService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.LookupExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupBackgroundJobHandler implements BackgroundJobHandler {

    private final LookupService lookupService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.LOOKUP;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        LookupJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            LookupExecutionResultDto result = lookupService.execute(userId, payload.lookupSessionId());
            log.info(
                    "Processed LOOKUP job jobId={} userId={} lookupSessionId={} outcome={} contextCount={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    result.outcome(), result.contextCount()
            );
        } catch (LookupExecutionException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) {
                lookupService.markFailed(userId, payload.lookupSessionId(), exception.errorCode());
            }
            log.warn(
                    "Lookup execution failed jobId={} userId={} lookupSessionId={} failureKind={} "
                            + "errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    exception.failureKind(), exception.errorCode(), terminal
            );
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                lookupService.markFailed(userId, payload.lookupSessionId(), "LOOKUP_FAILED");
            }
            log.error(
                    "Unexpected lookup failure jobId={} userId={} lookupSessionId={} attempt={} "
                            + "maxAttempts={} exceptionType={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    job.attempts(), job.maxAttempts(), exception.getClass().getSimpleName()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "LOOKUP_FAILED", exception
            );
        }
    }

    private LookupJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.LOOKUP || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return LookupJobPayloadDto.from(job.payload());
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
