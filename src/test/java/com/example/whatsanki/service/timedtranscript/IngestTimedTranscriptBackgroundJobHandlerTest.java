package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IngestTimedTranscriptBackgroundJobHandlerTest {
    private final TimedTranscriptIngestionService ingestion = mock(TimedTranscriptIngestionService.class);
    private final IngestTimedTranscriptBackgroundJobHandler handler =
            new IngestTimedTranscriptBackgroundJobHandler(ingestion);

    @Test
    void permanentFailureIsTerminalOnTheFirstAttempt() {
        ClaimedBackgroundJobDto job = job(1, 4);
        whenFailure(job, TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED"));

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.failureKind())
                            .isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                            .isEqualTo("TIMED_TRANSCRIPT_MALFORMED");
                });
        verify(ingestion).markFailedAndDelete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("TIMED_TRANSCRIPT_MALFORMED"));
    }

    @Test
    void transientFailureRetainsUploadUntilTheFinalAttempt() {
        ClaimedBackgroundJobDto retryable = job(1, 4);
        whenFailure(retryable, TimedTranscriptException.transientFailure(
                "TIMED_TRANSCRIPT_PROCESSING_FAILED", new IllegalStateException("temporary")));

        assertThatThrownBy(() -> handler.handle(retryable))
                .isInstanceOf(BackgroundJobExecutionException.class);
        verify(ingestion, never()).markFailedAndDelete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());

        ClaimedBackgroundJobDto exhausted = job(4, 4);
        whenFailure(exhausted, TimedTranscriptException.transientFailure(
                "TIMED_TRANSCRIPT_PROCESSING_FAILED", new IllegalStateException("still unavailable")));
        assertThatThrownBy(() -> handler.handle(exhausted))
                .isInstanceOf(BackgroundJobExecutionException.class);
        verify(ingestion).markFailedAndDelete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("TIMED_TRANSCRIPT_PROCESSING_FAILED"));
    }

    private void whenFailure(ClaimedBackgroundJobDto job, RuntimeException failure) {
        org.mockito.Mockito.doThrow(failure).when(ingestion).ingest(
                org.mockito.ArgumentMatchers.argThat(user -> user.value().equals(job.userId())),
                org.mockito.ArgumentMatchers.argThat(source ->
                        source.value().equals(UUID.fromString(String.valueOf(job.payload().get("sourceId")))))
        );
    }

    private ClaimedBackgroundJobDto job(int attempts, int maxAttempts) {
        return new ClaimedBackgroundJobDto(JobId.random(), UUID.randomUUID(),
                BackgroundJobEntity.Type.INGEST_TIMED_TRANSCRIPT,
                Map.of("schemaVersion", 1, "sourceId", UUID.randomUUID().toString()), attempts, maxAttempts);
    }
}
