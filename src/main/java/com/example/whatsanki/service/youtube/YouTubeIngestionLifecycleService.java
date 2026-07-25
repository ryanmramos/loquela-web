package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.YouTubeIngestionPreparationDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class YouTubeIngestionLifecycleService {
    private final SourceDocumentRepository sourceDocumentRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final PassageRepository passageRepository;
    private final TextNormalizer textNormalizer;

    @Transactional
    public YouTubeIngestionPreparationDto prepare(UserId userId, SourceId sourceId) {
        SourceDocumentEntity source = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> permanent("YOUTUBE_SOURCE_NOT_FOUND"));
        if (source.getKind() != SourceDocumentEntity.Kind.YOUTUBE_VIDEO) throw permanent("INVALID_YOUTUBE_SOURCE");
        if (source.getStatus() == SourceDocumentEntity.Status.READY) {
            VideoSourceEntity video = video(userId, sourceId);
            return new YouTubeIngestionPreparationDto(sourceId, video.getProviderVideoId(), source.getLanguage(), true);
        }
        if (source.getStatus() == SourceDocumentEntity.Status.DELETING) throw permanent("YOUTUBE_SOURCE_DELETING");
        if (source.getStatus() != SourceDocumentEntity.Status.RECEIVED
                && source.getStatus() != SourceDocumentEntity.Status.FETCHING) {
            throw permanent("INVALID_YOUTUBE_SOURCE_STATE");
        }
        source.setStatus(SourceDocumentEntity.Status.FETCHING);
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        VideoSourceEntity video = video(userId, sourceId);
        return new YouTubeIngestionPreparationDto(sourceId, video.getProviderVideoId(), source.getLanguage(), false);
    }

    @Transactional
    public void complete(
            UserId userId,
            SourceId sourceId,
            YouTubeSubtitleResultDto result,
            List<PassageDraftDto> drafts
    ) {
        SourceDocumentEntity source = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> permanent("YOUTUBE_SOURCE_NOT_FOUND"));
        if (source.getStatus() == SourceDocumentEntity.Status.READY) return;
        if (source.getStatus() != SourceDocumentEntity.Status.FETCHING) throw permanent("INVALID_YOUTUBE_SOURCE_STATE");
        VideoSourceEntity video = video(userId, sourceId);

        source.setStatus(SourceDocumentEntity.Status.INDEXING);
        passageRepository.deleteAllByUserIdAndSourceId(userId.value(), sourceId.value());
        int ordinal = 0;
        long characters = 0;
        for (PassageDraftDto draft : drafts) {
            PassageEntity passage = new PassageEntity();
            passage.setUserId(userId.value());
            passage.setSourceId(sourceId.value());
            passage.setOrdinal(ordinal++);
            passage.setDisplayText(draft.displayText());
            passage.setNormalizedText(textNormalizer.normalizeForSearch(draft.displayText()));
            passage.setLocation(draft.location());
            passage.setTokenCount(draft.tokenCount());
            passageRepository.save(passage);
            characters += draft.displayText().length();
        }

        video.setTitle(result.title());
        video.setChannelName(result.channelName());
        video.setDurationMs(result.durationMs());
        video.setCaptionLanguage(result.captionLanguage());
        video.setCaptionKind(result.captionKind());
        video.setProviderVersion(result.providerVersion());
        video.setRetrievedAt(result.retrievedAt());
        videoSourceRepository.save(video);

        source.setDisplayName(result.title());
        source.setCharacterCount(characters);
        source.setPassageCount(drafts.size());
        source.setMetadata(new LinkedHashMap<>(java.util.Map.of(
                "provider", "YOUTUBE",
                "videoId", video.getProviderVideoId(),
                "captionKind", result.captionKind().name(),
                "providerVersion", result.providerVersion()
        )));
        source.setStatus(SourceDocumentEntity.Status.READY);
        sourceDocumentRepository.save(source);
        log.info("Indexed YouTube captions userId={} sourceId={} passageCount={} characterCount={} captionKind={}",
                userId.value(), sourceId.value(), drafts.size(), characters, result.captionKind());
    }

    @Transactional
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()).ifPresent(source -> {
            if (source.getStatus() != SourceDocumentEntity.Status.READY
                    && source.getStatus() != SourceDocumentEntity.Status.DELETING) {
                source.setStatus(SourceDocumentEntity.Status.FAILED);
                source.setErrorCode(errorCode);
                source.setErrorDetailSafe(null);
                sourceDocumentRepository.save(source);
            }
        });
    }

    private VideoSourceEntity video(UserId userId, SourceId sourceId) {
        return videoSourceRepository.findBySourceIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> permanent("YOUTUBE_VIDEO_METADATA_NOT_FOUND"));
    }

    private YouTubeSubtitleException permanent(String code) {
        return new YouTubeSubtitleException(BackgroundJobFailureKind.PERMANENT, code);
    }
}
