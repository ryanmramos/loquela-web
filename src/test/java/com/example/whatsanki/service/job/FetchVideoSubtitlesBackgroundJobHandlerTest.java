package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.FetchVideoSubtitlesJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import com.example.whatsanki.service.youtube.YouTubeIngestionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FetchVideoSubtitlesBackgroundJobHandlerTest {
    private final YouTubeIngestionService ingestionService = mock(YouTubeIngestionService.class);
    private final FetchVideoSubtitlesBackgroundJobHandler handler =
            new FetchVideoSubtitlesBackgroundJobHandler(ingestionService);

    @Test
    void permanentlyMissingCaptionsMarkSourceFailed() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        var job = job(userId, sourceId, 1, 3);
        doThrow(new YouTubeSubtitleException(
                BackgroundJobFailureKind.PERMANENT, "YOUTUBE_CAPTIONS_NOT_FOUND"
        )).when(ingestionService).fetchAndIndex(userId, sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("YOUTUBE_CAPTIONS_NOT_FOUND");
                });
        verify(ingestionService).markFailed(userId, sourceId, "YOUTUBE_CAPTIONS_NOT_FOUND");
    }

    @Test
    void transientFailureWaitsUntilFinalAttemptBeforeMarkingSourceFailed() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        var job = job(userId, sourceId, 1, 3);
        doThrow(new YouTubeSubtitleException(
                BackgroundJobFailureKind.TRANSIENT, "YOUTUBE_PROVIDER_TEMPORARY_FAILURE"
        )).when(ingestionService).fetchAndIndex(userId, sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(BackgroundJobExecutionException.class);

        verify(ingestionService, never()).markFailed(userId, sourceId, "YOUTUBE_PROVIDER_TEMPORARY_FAILURE");
    }

    private ClaimedBackgroundJobDto job(UserId userId, SourceId sourceId, int attempt, int maximum) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), userId.value(), BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES,
                FetchVideoSubtitlesJobPayloadDto.create(sourceId).toMap(), attempt, maximum
        );
    }
}
