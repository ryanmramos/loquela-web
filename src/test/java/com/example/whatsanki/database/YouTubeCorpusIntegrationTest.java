package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import com.example.whatsanki.service.lookup.CorpusSearch;
import com.example.whatsanki.service.youtube.YouTubeIngestionLifecycleService;
import com.example.whatsanki.service.youtube.YouTubeIngestionServiceImpl;
import com.example.whatsanki.service.youtube.YouTubePassageBuilder;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeCorpusIntegrationTest extends PostgreSqlIntegrationTest {
    private final AppUserRepository appUserRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final YouTubeIngestionLifecycleService lifecycleService;
    private final YouTubePassageBuilder passageBuilder;
    private final WhatsAnkiConfigurationDto configuration;
    private final CorpusSearch corpusSearch;

    YouTubeCorpusIntegrationTest(
            AppUserRepository appUserRepository,
            SourceDocumentRepository sourceDocumentRepository,
            VideoSourceRepository videoSourceRepository,
            YouTubeIngestionLifecycleService lifecycleService,
            YouTubePassageBuilder passageBuilder,
            WhatsAnkiConfigurationDto configuration,
            CorpusSearch corpusSearch
    ) {
        this.appUserRepository = appUserRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.lifecycleService = lifecycleService;
        this.passageBuilder = passageBuilder;
        this.configuration = configuration;
        this.corpusSearch = corpusSearch;
    }

    @Test
    void fetchedCaptionsBecomeTimestampedLookupContext() {
        UserId userId = user();
        SourceId sourceId = source(userId);
        YouTubeSubtitleResultDto result = new YouTubeSubtitleResultDto(
                "Spanish lesson", "Spanish School", 90_000L, "es",
                VideoSourceEntity.CaptionKind.MANUAL, "2026.07.20", Instant.now(),
                List.of(new TimedCaptionCueDto(8_000, 12_000, "Quiero vivir aquí."))
        );
        var ingestion = new YouTubeIngestionServiceImpl(
                lifecycleService, (videoId, language, automaticFallback) -> result,
                passageBuilder, configuration
        );

        ingestion.fetchAndIndex(userId, sourceId);

        SourceDocumentEntity source = sourceDocumentRepository
                .findByIdAndUserId(sourceId.value(), userId.value()).orElseThrow();
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(source.getDisplayName()).isEqualTo("Spanish lesson");

        var matches = corpusSearch.findExamples(
                userId, "vivir", new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 5)
        );
        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.sourceKind()).isEqualTo(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
            assertThat(match.sourceDisplayName()).isEqualTo("Spanish lesson");
            assertThat(match.location())
                    .containsEntry("videoId", "dQw4w9WgXcQ")
                    .containsEntry("channelName", "Spanish School");
            assertThat(((Number) match.location().get("startMs")).longValue()).isEqualTo(8_000L);
            assertThat(((Number) match.location().get("playbackStartMs")).longValue()).isEqualTo(5_000L);
        });
    }

    private UserId user() {
        String identity = "youtube-owner-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private SourceId source(UserId userId) {
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
        source.setDisplayName("YouTube · dQw4w9WgXcQ");
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setLanguage("es");
        source.setMetadata(new LinkedHashMap<>());
        sourceDocumentRepository.save(source);

        VideoSourceEntity video = new VideoSourceEntity();
        video.setSourceId(sourceId.value());
        video.setUserId(userId.value());
        video.setProvider(VideoSourceEntity.Provider.YOUTUBE);
        video.setProviderVideoId("dQw4w9WgXcQ");
        video.setCanonicalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        video.setCaptionLanguage("es");
        video.setCaptionKind(VideoSourceEntity.CaptionKind.PENDING);
        videoSourceRepository.save(video);
        return sourceId;
    }
}
