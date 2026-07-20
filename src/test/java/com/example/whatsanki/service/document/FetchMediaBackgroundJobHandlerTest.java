package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.FetchMediaJobPayloadDto;
import com.example.whatsanki.service.document.DocumentIngestionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FetchMediaBackgroundJobHandlerTest {

    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final FetchMediaBackgroundJobHandler handler = new FetchMediaBackgroundJobHandler(ingestionService);

    @Test
    void delegatesValidIdOnlyJob() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(sourceId, 1, 3);

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();

        verify(ingestionService).fetchAndStore(new UserId(job.userId()), sourceId);
    }

    @Test
    void leavesTransientFailureRetryableUntilFinalAttempt() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(sourceId, 1, 3);
        doThrow(MediaAcquisitionException.transientFailure("MEDIA_DOWNLOAD_FAILED", null))
                .when(ingestionService).fetchAndStore(new UserId(job.userId()), sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("MEDIA_DOWNLOAD_FAILED");
                });
        verify(ingestionService, never()).markFailed(new UserId(job.userId()), sourceId,
                "MEDIA_DOWNLOAD_FAILED", true);
    }

    @Test
    void permanentlyRejectsBadMediaAndClearsStaging() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(sourceId, 1, 3);
        doThrow(MediaAcquisitionException.permanent("MEDIA_TYPE_MISMATCH"))
                .when(ingestionService).fetchAndStore(new UserId(job.userId()), sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(BackgroundJobExecutionException.class);
        verify(ingestionService).markFailed(
                new UserId(job.userId()), sourceId, "MEDIA_TYPE_MISMATCH", true
        );
    }

    private ClaimedBackgroundJobDto job(SourceId sourceId, int attempts, int maxAttempts) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), UUID.randomUUID(), BackgroundJobEntity.Type.FETCH_MEDIA,
                FetchMediaJobPayloadDto.create(sourceId).toMap(), attempts, maxAttempts
        );
    }
}
