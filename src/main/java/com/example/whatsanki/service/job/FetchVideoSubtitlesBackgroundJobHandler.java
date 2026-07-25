package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.FetchVideoSubtitlesJobPayloadDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import com.example.whatsanki.service.youtube.YouTubeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FetchVideoSubtitlesBackgroundJobHandler implements BackgroundJobHandler {
    private final YouTubeIngestionService ingestionService;

    @Override
    public BackgroundJobEntity.Type supportedType() {
        return BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES;
    }

    @Override
    public void handle(ClaimedBackgroundJobDto job) {
        FetchVideoSubtitlesJobPayloadDto payload = parse(job);
        UserId userId = new UserId(job.userId());
        try {
            ingestionService.fetchAndIndex(userId, payload.sourceId());
            log.info("Processed FETCH_VIDEO_SUBTITLES job jobId={} userId={} sourceId={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value());
        } catch (YouTubeSubtitleException exception) {
            boolean terminal = exception.failureKind() == BackgroundJobFailureKind.PERMANENT
                    || job.attempts() >= job.maxAttempts();
            if (terminal) ingestionService.markFailed(userId, payload.sourceId(), exception.errorCode());
            log.warn("YouTube subtitle ingestion failed jobId={} userId={} sourceId={} failureKind={} errorCode={} terminal={}",
                    job.jobId().value(), userId.value(), payload.sourceId().value(),
                    exception.failureKind(), exception.errorCode(), terminal);
            throw new BackgroundJobExecutionException(exception.failureKind(), exception.errorCode(), exception);
        }
    }

    private FetchVideoSubtitlesJobPayloadDto parse(ClaimedBackgroundJobDto job) {
        if (job.type() != BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES || job.userId() == null) {
            throw invalid(null);
        }
        try {
            return FetchVideoSubtitlesJobPayloadDto.from(job.payload());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private BackgroundJobExecutionException invalid(Throwable cause) {
        return new BackgroundJobExecutionException(BackgroundJobFailureKind.PERMANENT, "INVALID_JOB_PAYLOAD", cause);
    }
}
