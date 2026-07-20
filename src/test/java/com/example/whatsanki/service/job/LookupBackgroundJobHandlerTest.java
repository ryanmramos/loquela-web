package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.dto.LookupJobPayloadDto;
import com.example.whatsanki.service.lookup.LookupService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.LookupExecutionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupBackgroundJobHandlerTest {

    private final LookupService lookupService = mock(LookupService.class);
    private final LookupBackgroundJobHandler handler = new LookupBackgroundJobHandler(lookupService);

    @Test
    void handlesValidLookupJob() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        when(lookupService.execute(new UserId(job.userId()), sessionId)).thenReturn(
                new LookupExecutionResultDto(sessionId, LookupExecutionResultDto.Outcome.COMPLETED, 2)
        );

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();
    }

    @Test
    void deadLettersDeterministicFailuresAndMarksSessionFailed() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        doThrow(LookupExecutionException.permanent("DEFINITION_NOT_FOUND"))
                .when(lookupService).execute(new UserId(job.userId()), sessionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("DEFINITION_NOT_FOUND");
                });
        verify(lookupService).markFailed(
                new UserId(job.userId()), sessionId, "DEFINITION_NOT_FOUND"
        );
    }

    @Test
    void retriesUnexpectedProviderFailuresAndOnlyClosesSessionOnFinalAttempt() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto retryable = job(sessionId, 1, 3);
        doThrow(new IllegalStateException("provider unavailable"))
                .when(lookupService).execute(new UserId(retryable.userId()), sessionId);

        assertThatThrownBy(() -> handler.handle(retryable))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("LOOKUP_FAILED");
                });

        ClaimedBackgroundJobDto finalAttempt = new ClaimedBackgroundJobDto(
                JobId.random(), retryable.userId(), BackgroundJobEntity.Type.LOOKUP,
                LookupJobPayloadDto.create(sessionId).toMap(), 3, 3
        );
        assertThatThrownBy(() -> handler.handle(finalAttempt))
                .isInstanceOf(BackgroundJobExecutionException.class);
        verify(lookupService).markFailed(
                new UserId(finalAttempt.userId()), sessionId, "LOOKUP_FAILED"
        );
    }

    @Test
    void preservesClassifiedProviderTimeoutAcrossJobBoundary() {
        LookupSessionId sessionId = LookupSessionId.random();
        ClaimedBackgroundJobDto job = job(sessionId, 1, 3);
        doThrow(new LookupExecutionException(
                BackgroundJobFailureKind.TRANSIENT,
                "PROVIDER_TIMEOUT",
                new RuntimeException("provider details stay internal")
        )).when(lookupService).execute(new UserId(job.userId()), sessionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("PROVIDER_TIMEOUT");
                });
        verify(lookupService, never()).markFailed(
                new UserId(job.userId()), sessionId, "PROVIDER_TIMEOUT"
        );

        ClaimedBackgroundJobDto finalAttempt = new ClaimedBackgroundJobDto(
                JobId.random(), job.userId(), BackgroundJobEntity.Type.LOOKUP,
                LookupJobPayloadDto.create(sessionId).toMap(), 3, 3
        );
        assertThatThrownBy(() -> handler.handle(finalAttempt))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo("PROVIDER_TIMEOUT"));
        verify(lookupService).markFailed(
                new UserId(job.userId()), sessionId, "PROVIDER_TIMEOUT"
        );
    }

    private ClaimedBackgroundJobDto job(LookupSessionId sessionId, int attempts, int maxAttempts) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), UUID.randomUUID(), BackgroundJobEntity.Type.LOOKUP,
                LookupJobPayloadDto.create(sessionId).toMap(), attempts, maxAttempts
        );
    }
}
