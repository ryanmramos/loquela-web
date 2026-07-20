package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeleteTwilioMediaJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.TwilioMediaCleanupException;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteTwilioMediaBackgroundJobHandler implements BackgroundJobHandler {

    private final TwilioMediaCleanupService cleanupService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.DELETE_TWILIO_MEDIA;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        DeleteTwilioMediaJobPayloadDto payload = parsePayload(job);
        UserId userId = new UserId(job.userId());
        try {
            cleanupService.delete(userId, payload.inboundMessageId());
            log.info("Processed DELETE_TWILIO_MEDIA job jobId={} userId={} inboundMessageId={}",
                    job.jobId().value(), userId.value(), payload.inboundMessageId().value());
        } catch (TwilioMediaCleanupException exception) {
            throw new BackgroundJobExecutionException(
                    exception.failureKind(), exception.errorCode(), exception
            );
        }
    }

    private DeleteTwilioMediaJobPayloadDto parsePayload(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.DELETE_TWILIO_MEDIA || job.userId() == null) {
            throw invalidPayload(null);
        }
        try {
            return DeleteTwilioMediaJobPayloadDto.from(job.payload());
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
