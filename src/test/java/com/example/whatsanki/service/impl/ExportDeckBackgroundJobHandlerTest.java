package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.ExportDeckJobPayloadDto;
import com.example.whatsanki.service.DeckExportService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.DeckExportException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExportDeckBackgroundJobHandlerTest {
    private final DeckExportService exportService = mock(DeckExportService.class);
    private final ExportDeckBackgroundJobHandler handler = new ExportDeckBackgroundJobHandler(exportService);

    @Test
    void executesValidIdOnlyJob() {
        ExportRequestId exportId = ExportRequestId.random();
        ClaimedBackgroundJobDto job = job(exportId, 1, 3);

        handler.handle(job);

        verify(exportService).execute(new UserId(job.userId()), exportId);
    }

    @Test
    void leavesTransientFailureRetryableUntilLastAttempt() {
        ExportRequestId exportId = ExportRequestId.random();
        ClaimedBackgroundJobDto job = job(exportId, 1, 3);
        doThrow(DeckExportException.transientFailure("EXPORTER_TIMEOUT", null))
                .when(exportService).execute(new UserId(job.userId()), exportId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("EXPORTER_TIMEOUT");
                });
        verify(exportService, never()).markFailed(new UserId(job.userId()), exportId, "EXPORTER_TIMEOUT");
    }

    @Test
    void marksPermanentFailureWithoutChangingCards() {
        ExportRequestId exportId = ExportRequestId.random();
        ClaimedBackgroundJobDto job = job(exportId, 1, 3);
        doThrow(DeckExportException.permanent("EXPORT_SNAPSHOT_INVALID"))
                .when(exportService).execute(new UserId(job.userId()), exportId);

        assertThatThrownBy(() -> handler.handle(job)).isInstanceOf(BackgroundJobExecutionException.class);
        verify(exportService).markFailed(
                new UserId(job.userId()), exportId, "EXPORT_SNAPSHOT_INVALID"
        );
    }

    private ClaimedBackgroundJobDto job(ExportRequestId exportId, int attempt, int maxAttempts) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), UUID.randomUUID(), BackgroundJobEntity.Type.EXPORT_DECK,
                ExportDeckJobPayloadDto.create(exportId).toMap(), attempt, maxAttempts
        );
    }
}
