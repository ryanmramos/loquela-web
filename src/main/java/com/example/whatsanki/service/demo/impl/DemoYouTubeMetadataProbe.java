package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YtDlpProcessResultDto;
import com.example.whatsanki.service.exception.DemoException;
import com.example.whatsanki.service.youtube.YtDlpProcessRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
class DemoYouTubeMetadataProbe {
    private final YtDlpProcessRunner runner;
    private final ObjectMapper objectMapper;
    private final WhatsAnkiConfigurationDto configuration;

    void requireAllowedDuration(String videoId) {
        Path directory = temporaryDirectory();
        try {
            String url = "https://www.youtube.com/watch?v=" + videoId;
            YtDlpProcessResultDto result = runner.run(
                    List.of("--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings", url),
                    directory, configuration.getYoutube().getProcessTimeout(),
                    configuration.getYoutube().getMaxMetadataBytes(),
                    configuration.getYoutube().getMaxCapturedErrorBytes()
            );
            if (result.exitCode() != 0) throw problem("YOUTUBE_VIDEO_UNAVAILABLE", null);
            JsonNode metadata = objectMapper.readTree(result.stdout());
            if (metadata.path("is_live").asBoolean(false) || "is_live".equals(metadata.path("live_status").asText())) {
                throw problem("YOUTUBE_VIDEO_UNAVAILABLE", null);
            }
            if (!metadata.path("duration").isNumber()) throw problem("YOUTUBE_VIDEO_UNAVAILABLE", null);
            long durationMs = Math.round(metadata.path("duration").asDouble() * 1_000D);
            if (durationMs > configuration.getDemo().getYoutubeMaxDuration().toMillis()) {
                throw problem("YOUTUBE_TOO_LONG", null);
            }
        } catch (DemoException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw problem("YOUTUBE_VIDEO_UNAVAILABLE", exception);
        } finally {
            deleteTree(directory);
        }
    }

    private Path temporaryDirectory() {
        try { return Files.createTempDirectory("loquela-demo-youtube-probe-"); }
        catch (IOException exception) { throw problem("DEMO_BUSY", exception); }
    }

    private void deleteTree(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private DemoException problem(String code, Throwable cause) {
        return new DemoException(HttpStatus.UNPROCESSABLE_ENTITY, code, null, cause);
    }
}
