package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YtDlpProcessResultDto;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YtDlpYouTubeSubtitleProviderTest {
    @Mock private YtDlpProcessRunner processRunner;

    private YtDlpYouTubeSubtitleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new YtDlpYouTubeSubtitleProvider(
                processRunner, new WebVttCaptionParser(), new ObjectMapper(), new WhatsAnkiConfigurationDto()
        );
    }

    @Test
    void choosesHumanCaptionBeforeAutomaticCaptionInTargetLanguage() {
        stubProvider("""
                {"title":"Spanish lesson","channel":"Teacher","duration":90,
                 "subtitles":{"es":[{"url":"https://captions/manual"}]},
                 "automatic_captions":{"es":[{"url":"https://captions/auto"}]}}
                """);

        var result = provider.fetch("dQw4w9WgXcQ", "es");

        assertThat(result.captionKind()).isEqualTo(VideoSourceEntity.CaptionKind.MANUAL);
        assertThat(result.captionLanguage()).isEqualTo("es");
        assertThat(result.title()).isEqualTo("Spanish lesson");
        assertThat(result.cues()).hasSize(1);
    }

    @Test
    void fallsBackToCompatibleAutomaticCaptionOnlyWhenManualIsAbsent() {
        stubProvider("""
                {"title":"Spanish lesson",
                 "subtitles":{"en":[{"url":"https://captions/en"}]},
                 "automatic_captions":{"es-419":[{"url":"https://captions/auto"}]}}
                """);

        var result = provider.fetch("dQw4w9WgXcQ", "es");

        assertThat(result.captionKind()).isEqualTo(VideoSourceEntity.CaptionKind.AUTOMATIC);
        assertThat(result.captionLanguage()).isEqualTo("es-419");
    }

    @Test
    void permanentlyRejectsVideoWithoutTargetLanguageCaptions() {
        when(processRunner.run(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(ok("2026.07.20"))
                .thenReturn(ok("{\"title\":\"No Spanish\",\"subtitles\":{},\"automatic_captions\":{}}"));

        assertThatThrownBy(() -> provider.fetch("dQw4w9WgXcQ", "es"))
                .isInstanceOfSatisfying(YouTubeSubtitleException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("YOUTUBE_CAPTIONS_NOT_FOUND");
                });
    }

    @Test
    void treatsBaseLanguageAsCompatibleWithRequestedRegionalLanguage() {
        stubProvider("""
                {"title":"Spanish lesson",
                 "subtitles":{"es":[{"url":"https://captions/manual"}]},
                 "automatic_captions":{}}
                """);

        var result = provider.fetch("dQw4w9WgXcQ", "es-MX");

        assertThat(result.captionKind()).isEqualTo(VideoSourceEntity.CaptionKind.MANUAL);
        assertThat(result.captionLanguage()).isEqualTo("es");
    }

    private void stubProvider(String metadata) {
        when(processRunner.run(any(), any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> arguments = invocation.getArgument(0);
            if (arguments.contains("--version")) return ok("2026.07.20");
            if (arguments.contains("--dump-single-json")) return ok(metadata);
            Path directory = invocation.getArgument(1);
            Files.writeString(directory.resolve("caption.es.vtt"), """
                    WEBVTT

                    00:00:04.000 --> 00:00:06.000
                    Quiero vivir aquí.
                    """);
            return ok("");
        });
    }

    private YtDlpProcessResultDto ok(String stdout) {
        return new YtDlpProcessResultDto(0, stdout.getBytes(StandardCharsets.UTF_8), new byte[0]);
    }
}
