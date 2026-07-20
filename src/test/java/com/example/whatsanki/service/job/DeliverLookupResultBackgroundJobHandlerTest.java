package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeliverLookupResultJobPayloadDto;
import com.example.whatsanki.dto.LookupDeliveryResultDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import com.example.whatsanki.service.lookup.LookupResultDeliveryService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverLookupResultBackgroundJobHandlerTest {
    private final LookupResultDeliveryService deliveryService = mock(LookupResultDeliveryService.class);
    private final DeliverLookupResultBackgroundJobHandler handler =
            new DeliverLookupResultBackgroundJobHandler(deliveryService);

    @Test
    void deliversValidJob() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        when(deliveryService.deliver(new UserId(job.userId()), sessionId))
                .thenReturn(new LookupDeliveryResultDto(2, 2));

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();
    }

    @Test
    void deadLettersPermanentFailureAndMarksPendingPartsFailed() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        doThrow(OutboundDeliveryException.permanent("LOOKUP_RESULT_EXPIRED"))
                .when(deliveryService).deliver(new UserId(job.userId()), sessionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("LOOKUP_RESULT_EXPIRED");
                });
        verify(deliveryService).markFailed(
                new UserId(job.userId()), sessionId, "LOOKUP_RESULT_EXPIRED"
        );
    }

    @Test
    void retriesTransientFailureWithoutClosingPartsBeforeFinalAttempt() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        doThrow(OutboundDeliveryException.transientFailure(
                "TWILIO_TEMPORARY_FAILURE", new IllegalStateException("unavailable")
        )).when(deliveryService).deliver(new UserId(job.userId()), sessionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("TWILIO_TEMPORARY_FAILURE");
                });
    }

    private ClaimedBackgroundJobDto job(
            LookupSessionId sessionId,
            int attempts,
            int maxAttempts
    ) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), UUID.randomUUID(), BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                DeliverLookupResultJobPayloadDto.create(sessionId).toMap(), attempts, maxAttempts
        );
    }
}
