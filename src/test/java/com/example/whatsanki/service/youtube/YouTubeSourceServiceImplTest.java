package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.exception.YouTubeIngestionDisabledException;
import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeSourceServiceImplTest {
    @Mock private AppUserRepository appUserRepository;
    @Mock private InboundMessageRepository inboundMessageRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private VideoSourceRepository videoSourceRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private BackgroundJobService backgroundJobService;

    private YouTubeSourceServiceImpl service;
    private WhatsAnkiConfigurationDto configuration;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new YouTubeSourceServiceImpl(
                appUserRepository, inboundMessageRepository, sourceDocumentRepository,
                videoSourceRepository, backgroundJobRepository, backgroundJobService,
                new YouTubeUrlPolicy(), configuration
        );
    }

    @Test
    void rejectsNewSourcesWhenIndependentKillSwitchIsOff() {
        configuration.getYoutube().setEnabled(false);

        assertThatThrownBy(() -> service.acceptWebsite(UserId.random(), "https://youtu.be/dQw4w9WgXcQ"))
                .isInstanceOf(YouTubeIngestionDisabledException.class)
                .hasMessage("YOUTUBE_INGESTION_DISABLED");
    }

    @Test
    void snapshotsSourceLanguageAndQueuesOnlySourceId() {
        UserId userId = UserId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setId(inboundId.value());
        inbound.setUserId(userId.value());
        inbound.setCommandType(InboundMessageEntity.CommandType.YOUTUBE);
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es-MX");
        user.setAutomaticCaptionFallback(false);
        JobId jobId = JobId.random();

        when(sourceDocumentRepository.findByUserIdAndInboundMessageId(userId.value(), inboundId.value()))
                .thenReturn(Optional.empty());
        when(inboundMessageRepository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(videoSourceRepository.findByUserIdAndProviderAndProviderVideoIdAndCaptionLanguage(
                userId.value(), VideoSourceEntity.Provider.YOUTUBE, "dQw4w9WgXcQ", "es-MX"
        )).thenReturn(Optional.empty());
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES), any(Map.class)))
                .thenReturn(jobId);

        var accepted = service.accept(userId, inboundId, "https://youtu.be/dQw4w9WgXcQ");

        ArgumentCaptor<SourceDocumentEntity> source = ArgumentCaptor.forClass(SourceDocumentEntity.class);
        ArgumentCaptor<VideoSourceEntity> video = ArgumentCaptor.forClass(VideoSourceEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sourceDocumentRepository).save(source.capture());
        verify(videoSourceRepository).save(video.capture());
        verify(backgroundJobService).enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES), payload.capture()
        );

        assertThat(accepted.jobId()).isEqualTo(jobId);
        assertThat(source.getValue().getKind()).isEqualTo(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
        assertThat(source.getValue().getStatus()).isEqualTo(SourceDocumentEntity.Status.RECEIVED);
        assertThat(source.getValue().getLanguage()).isEqualTo("es-MX");
        assertThat(source.getValue().getMetadata())
                .containsEntry("automaticCaptionFallback", false);
        assertThat(video.getValue().getProviderVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(video.getValue().getCaptionKind()).isEqualTo(VideoSourceEntity.CaptionKind.PENDING);
        assertThat(payload.getValue()).containsOnlyKeys("schemaVersion", "sourceId");
        assertThat(payload.getValue()).containsEntry("sourceId", accepted.sourceId().value().toString());
    }

    @Test
    void websiteAcceptanceDoesNotInventAWhatsAppMessage() {
        UserId userId = UserId.random();
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es");
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(videoSourceRepository.findByUserIdAndProviderAndProviderVideoIdAndCaptionLanguage(
                userId.value(), VideoSourceEntity.Provider.YOUTUBE, "dQw4w9WgXcQ", "es"
        )).thenReturn(Optional.empty());
        when(backgroundJobService.enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES), any(Map.class)
        )).thenReturn(JobId.random());

        service.acceptWebsite(userId, "https://youtu.be/dQw4w9WgXcQ");

        ArgumentCaptor<SourceDocumentEntity> source = ArgumentCaptor.forClass(SourceDocumentEntity.class);
        verify(sourceDocumentRepository).save(source.capture());
        assertThat(source.getValue().getInboundMessageId()).isNull();
        assertThat(source.getValue().getMetadata()).containsEntry("origin", "WEBSITE");
    }
}
