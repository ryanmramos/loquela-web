package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YouTubeIngestionPreparationDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YouTubeIngestionServiceImpl implements YouTubeIngestionService {
    private final YouTubeIngestionLifecycleService lifecycleService;
    private final YouTubeSubtitleProvider subtitleProvider;
    private final YouTubePassageBuilder passageBuilder;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public void fetchAndIndex(UserId userId, SourceId sourceId) {
        YouTubeIngestionPreparationDto preparation = lifecycleService.prepare(userId, sourceId);
        if (preparation.alreadyReady()) return;
        YouTubeSubtitleResultDto result = subtitleProvider.fetch(
                preparation.videoId(), preparation.sourceLanguage()
        );
        List<PassageDraftDto> passages = passageBuilder.build(
                preparation.videoId(), result.channelName(), result.cues()
        );
        long characters = passages.stream().mapToLong(value -> value.displayText().length()).sum();
        if (passages.isEmpty() || characters > configuration.getIngestion().getMaxExtractedCharacters()) {
            throw new YouTubeSubtitleException(BackgroundJobFailureKind.PERMANENT, "YOUTUBE_CAPTIONS_TOO_LARGE");
        }
        lifecycleService.complete(userId, sourceId, result, passages);
    }

    @Override
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        lifecycleService.markFailed(userId, sourceId, errorCode);
    }
}
