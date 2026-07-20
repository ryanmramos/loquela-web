package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeliverLookupResultJobPayloadDto;
import com.example.whatsanki.dto.LookupDeliveryResultDto;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import com.example.whatsanki.service.lookup.LookupResultDeliveryService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliverLookupResultBackgroundJobHandler implements BackgroundJobHandler {
    private final LookupResultDeliveryService deliveryService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        DeliverLookupResultJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            LookupDeliveryResultDto result = deliveryService.deliver(
                    userId, payload.lookupSessionId()
            );
            log.info(
                    "Processed lookup delivery job jobId={} userId={} lookupSessionId={} "
                            + "newlyAcceptedParts={} totalParts={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    result.newlyAcceptedParts(), result.totalParts()
            );
        } catch (OutboundDeliveryException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) {
                deliveryService.markFailed(
                        userId, payload.lookupSessionId(), exception.errorCode()
                );
            }
            log.warn(
                    "Lookup delivery failed jobId={} userId={} lookupSessionId={} "
                            + "failureKind={} errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    exception.failureKind(), exception.errorCode(), terminal
            );
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        } catch (RuntimeException exception) {
            if (job.attempts() >= job.maxAttempts()) {
                deliveryService.markFailed(
                        userId, payload.lookupSessionId(), "OUTBOUND_DELIVERY_FAILED"
                );
            }
            log.error(
                    "Unexpected lookup delivery failure jobId={} userId={} lookupSessionId={} "
                            + "attempt={} maxAttempts={} exceptionType={}",
                    job.jobId().value(), userId.value(), payload.lookupSessionId().value(),
                    job.attempts(), job.maxAttempts(), exception.getClass().getSimpleName()
            );
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "OUTBOUND_DELIVERY_FAILED", exception
            );
        }
    }

    private DeliverLookupResultJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return DeliverLookupResultJobPayloadDto.from(job.payload());
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
