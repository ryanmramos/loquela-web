package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.FetchVideoSubtitlesJobPayloadDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YouTubeSourceAcceptanceDto;
import com.example.whatsanki.dto.YouTubeVideoReferenceDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.exception.YouTubeIngestionDisabledException;
import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Legacy YouTube acceptance path retained while older queued jobs drain. */
@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated(forRemoval = true)
public class YouTubeSourceServiceImpl implements YouTubeSourceService {
    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;
    private final YouTubeUrlPolicy urlPolicy;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public YouTubeSourceAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String rawUrl) {
        requireEnabled();
        YouTubeVideoReferenceDto reference = urlPolicy.parse(rawUrl);
        var existingInbound = sourceDocumentRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        );
        if (existingInbound.isPresent()) {
            SourceId sourceId = new SourceId(existingInbound.orElseThrow().getId());
            return new YouTubeSourceAcceptanceDto(sourceId, activeJob(userId, sourceId), true);
        }

        InboundMessageEntity inbound = inboundMessageRepository
                .findByIdAndUserId(inboundMessageId.value(), userId.value())
                .orElseThrow(() -> new IllegalStateException("Inbound message was not found for YouTube command"));
        if (inbound.getCommandType() != InboundMessageEntity.CommandType.YOUTUBE) {
            throw new IllegalStateException("Inbound message is not a YouTube command");
        }
        return acceptNew(userId, inboundMessageId, reference, "WHATSAPP");
    }

    @Override
    @Transactional
    public YouTubeSourceAcceptanceDto acceptWebsite(UserId userId, String rawUrl) {
        requireEnabled();
        return acceptNew(userId, null, urlPolicy.parse(rawUrl), "WEBSITE");
    }

    private void requireEnabled() {
        if (!configuration.getYoutube().isEnabled()) {
            throw new YouTubeIngestionDisabledException();
        }
    }

    private YouTubeSourceAcceptanceDto acceptNew(
            UserId userId,
            InboundMessageId inboundMessageId,
            YouTubeVideoReferenceDto reference,
            String origin
    ) {
        AppUserEntity user = appUserRepository.findById(userId.value())
                .filter(candidate -> candidate.getStatus() == AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active user was not found for YouTube source"));
        String language = new LanguageTag(user.getSourceLanguage()).value();

        var existingVideo = videoSourceRepository.findByUserIdAndProviderAndProviderVideoIdAndCaptionLanguage(
                userId.value(), VideoSourceEntity.Provider.YOUTUBE, reference.videoId(), language
        );
        if (existingVideo.isPresent()) {
            SourceId sourceId = new SourceId(existingVideo.orElseThrow().getSourceId());
            return new YouTubeSourceAcceptanceDto(sourceId, activeJob(userId, sourceId), true);
        }

        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setInboundMessageId(inboundMessageId == null ? null : inboundMessageId.value());
        source.setKind(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
        source.setDisplayName("YouTube · " + reference.videoId());
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setLanguage(language);
        // Snapshot the preference at acceptance so changing Settings cannot alter
        // an already queued or retried ingestion.
        source.setMetadata(new LinkedHashMap<>(Map.of(
                "origin", origin,
                "automaticCaptionFallback", user.isAutomaticCaptionFallback()
        )));
        sourceDocumentRepository.save(source);

        VideoSourceEntity video = new VideoSourceEntity();
        video.setSourceId(sourceId.value());
        video.setUserId(userId.value());
        video.setProvider(VideoSourceEntity.Provider.YOUTUBE);
        video.setProviderVideoId(reference.videoId());
        video.setCanonicalUrl(reference.canonicalUrl().toASCIIString());
        video.setCaptionLanguage(language);
        video.setCaptionKind(VideoSourceEntity.CaptionKind.PENDING);
        videoSourceRepository.save(video);

        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES,
                FetchVideoSubtitlesJobPayloadDto.create(sourceId).toMap()
        );
        log.info("Accepted YouTube subtitle source userId={} sourceId={} videoId={} sourceLanguage={}",
                userId.value(), sourceId.value(), reference.videoId(), language);
        return new YouTubeSourceAcceptanceDto(sourceId, jobId, false);
    }

    private JobId activeJob(UserId userId, SourceId sourceId) {
        return backgroundJobRepository.findActiveIdForSourceAndType(
                userId.value(), sourceId.value().toString(), BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES.name()
        ).map(JobId::new).orElse(null);
    }
}
