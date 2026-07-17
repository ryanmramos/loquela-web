package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeleteSourceJobPayloadDto;
import com.example.whatsanki.service.SourceDeletionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.SourceDeletionException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeleteSourceBackgroundJobHandlerTest {

    private final SourceDeletionService sourceDeletionService = mock(SourceDeletionService.class);
    private final DeleteSourceBackgroundJobHandler handler =
            new DeleteSourceBackgroundJobHandler(sourceDeletionService);

    @Test
    void delegatesAValidIdOnlyPayload() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(DeleteSourceJobPayloadDto.create(sourceId).toMap());

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();

        verify(sourceDeletionService).delete(
                new com.example.whatsanki.domain.UserId(job.userId()), sourceId, job.jobId()
        );
    }

    @Test
    void preservesTheDeletionFailureClassificationForTheRunner() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(DeleteSourceJobPayloadDto.create(sourceId).toMap());
        doThrow(new SourceDeletionException(BackgroundJobFailureKind.TRANSIENT, "SOURCE_BUSY"))
                .when(sourceDeletionService)
                .delete(new com.example.whatsanki.domain.UserId(job.userId()), sourceId, job.jobId());

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("SOURCE_BUSY");
                });
    }

    @Test
    void rejectsMalformedPayloadPermanently() {
        ClaimedBackgroundJobDto job = job(Map.of("schemaVersion", 1, "sourceId", "invalid"));

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("INVALID_JOB_PAYLOAD");
                });
    }

    private ClaimedBackgroundJobDto job(Map<String, Object> payload) {
        return new ClaimedBackgroundJobDto(
                JobId.random(),
                UUID.randomUUID(),
                BackgroundJobEntity.Type.DELETE_SOURCE,
                payload,
                1,
                3
        );
    }
}
