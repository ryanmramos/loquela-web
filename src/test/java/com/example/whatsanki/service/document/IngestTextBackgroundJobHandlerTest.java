package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.PastedTextIngestionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestTextBackgroundJobHandlerTest {

    @Mock
    private PastedTextIngestionService pastedTextIngestionService;

    @InjectMocks
    private IngestTextBackgroundJobHandler handler;

    @Test
    void registersAndDispatchesIngestTextPayload() {
        SourceId sourceId = SourceId.random();
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = job(userId, IngestTextJobPayloadDto.create(sourceId).toMap());

        assertThat(handler.supportedType()).isEqualTo(BackgroundJobEntity.Type.INGEST_TEXT);
        handler.handle(job);

        verify(pastedTextIngestionService).ingest(userId, sourceId);
    }

    @Test
    void rejectsMalformedPayloadAsPermanentFailure() {
        ClaimedBackgroundJobDto job = job(
                UserId.random(),
                Map.of("schemaVersion", 1, "sourceId", "invalid")
        );

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("INVALID_JOB_PAYLOAD");
                });
        verify(pastedTextIngestionService, never()).ingest(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void marksDeterministicSourceFailureAndDeadLettersJob() {
        SourceId sourceId = SourceId.random();
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = job(userId, IngestTextJobPayloadDto.create(sourceId).toMap());
        doThrow(new PastedTextIngestionException("EMPTY_TEXT"))
                .when(pastedTextIngestionService).ingest(userId, sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("EMPTY_TEXT");
                });
        verify(pastedTextIngestionService).markFailed(userId, sourceId, "EMPTY_TEXT");
    }

    @Test
    void classifiesUnexpectedFailureAsTransientBeforeAttemptLimit() {
        SourceId sourceId = SourceId.random();
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = job(userId, IngestTextJobPayloadDto.create(sourceId).toMap());
        doThrow(new IllegalStateException("temporary database problem"))
                .when(pastedTextIngestionService).ingest(userId, sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("INGEST_TEXT_FAILED");
                });
        verify(pastedTextIngestionService, never()).markFailed(userId, sourceId, "INGEST_TEXT_FAILED");
    }

    @Test
    void marksSourceFailedWhenUnexpectedFailureExhaustsAttempts() {
        SourceId sourceId = SourceId.random();
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = new ClaimedBackgroundJobDto(
                JobId.random(),
                userId.value(),
                BackgroundJobEntity.Type.INGEST_TEXT,
                IngestTextJobPayloadDto.create(sourceId).toMap(),
                3,
                3
        );
        doThrow(new IllegalStateException("persistent database problem"))
                .when(pastedTextIngestionService).ingest(userId, sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception ->
                        assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT));
        verify(pastedTextIngestionService).markFailed(userId, sourceId, "INGEST_TEXT_FAILED");
    }

    private ClaimedBackgroundJobDto job(UserId userId, Map<String, Object> payload) {
        return new ClaimedBackgroundJobDto(
                JobId.random(),
                userId.value(),
                BackgroundJobEntity.Type.INGEST_TEXT,
                payload,
                1,
                3
        );
    }
}
