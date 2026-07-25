package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.dto.YtDlpProcessResultDto;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class YtDlpYouTubeSubtitleProvider implements YouTubeSubtitleProvider {
    private final YtDlpProcessRunner processRunner;
    private final WebVttCaptionParser captionParser;
    private final ObjectMapper objectMapper;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public YouTubeSubtitleResultDto fetch(String videoId, String sourceLanguage) {
        String canonicalUrl = "https://www.youtube.com/watch?v=" + videoId;
        Path directory = createTemporaryDirectory();
        try {
            String version = version(directory);
            JsonNode metadata = metadata(canonicalUrl, directory);
            CaptionSelection selection = selectCaption(metadata, sourceLanguage);
            Path vtt = downloadCaption(canonicalUrl, selection, directory);
            long size = Files.size(vtt);
            if (size <= 0 || size > configuration.getYoutube().getMaxSubtitleBytes()) {
                throw permanent("YOUTUBE_CAPTIONS_TOO_LARGE", null);
            }
            String content = Files.readString(vtt, StandardCharsets.UTF_8);
            List<TimedCaptionCueDto> cues;
            try {
                cues = captionParser.parse(content, configuration.getYoutube().getMaxCaptionCues());
            } catch (IllegalArgumentException exception) {
                throw permanent("YOUTUBE_CAPTIONS_MALFORMED", exception);
            }
            String title = text(metadata, "title", "YouTube video");
            String channel = text(metadata, "channel", text(metadata, "uploader", ""));
            Long durationMs = metadata.path("duration").isNumber()
                    ? Math.max(0L, Math.round(metadata.path("duration").asDouble() * 1_000D)) : null;
            log.info("Fetched YouTube captions videoId={} captionLanguage={} captionKind={} cueCount={} providerVersion={}",
                    videoId, selection.language(), selection.kind(), cues.size(), version);
            return new YouTubeSubtitleResultDto(
                    title, channel, durationMs, canonicalLanguage(selection.language(), sourceLanguage),
                    selection.kind(), version, Instant.now(), cues
            );
        } catch (IOException exception) {
            throw transientFailure("YOUTUBE_CAPTION_IO_FAILED", exception);
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private String version(Path directory) {
        YtDlpProcessResultDto result = processRunner.run(
                List.of("--version"), directory, configuration.getYoutube().getProcessTimeout(),
                256, configuration.getYoutube().getMaxCapturedErrorBytes()
        );
        if (result.exitCode() != 0) throw classifyFailure(result);
        String version = new String(result.stdout(), StandardCharsets.UTF_8).strip();
        if (version.isBlank() || version.length() > 64) throw permanent("YTDLP_VERSION_INVALID", null);
        String expected = configuration.getYoutube().getExpectedVersion();
        if (expected != null && !expected.isBlank() && !expected.equals(version)) {
            throw permanent("YTDLP_VERSION_MISMATCH", null);
        }
        return version;
    }

    private JsonNode metadata(String canonicalUrl, Path directory) {
        YtDlpProcessResultDto result = processRunner.run(
                List.of("--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings", canonicalUrl),
                directory,
                configuration.getYoutube().getProcessTimeout(),
                configuration.getYoutube().getMaxMetadataBytes(),
                configuration.getYoutube().getMaxCapturedErrorBytes()
        );
        if (result.exitCode() != 0) throw classifyFailure(result);
        try {
            return objectMapper.readTree(result.stdout());
        } catch (RuntimeException exception) {
            throw permanent("YOUTUBE_METADATA_MALFORMED", exception);
        }
    }

    private CaptionSelection selectCaption(JsonNode metadata, String sourceLanguage) {
        String manual = compatibleLanguage(metadata.path("subtitles"), sourceLanguage);
        if (manual != null) return new CaptionSelection(manual, VideoSourceEntity.CaptionKind.MANUAL);
        if (configuration.getYoutube().isAutomaticCaptionFallback()) {
            String automatic = compatibleLanguage(metadata.path("automatic_captions"), sourceLanguage);
            if (automatic != null) return new CaptionSelection(automatic, VideoSourceEntity.CaptionKind.AUTOMATIC);
        }
        throw permanent("YOUTUBE_CAPTIONS_NOT_FOUND", null);
    }

    private String compatibleLanguage(JsonNode tracks, String requested) {
        if (!tracks.isObject()) return null;
        List<String> languages = new ArrayList<>();
        tracks.properties().forEach(field -> {
            if (hasDownloadableTrack(field.getValue())) languages.add(field.getKey());
        });
        String canonicalRequested = requested.toLowerCase(Locale.ROOT);
        return languages.stream().filter(value -> value.equalsIgnoreCase(requested)).findFirst()
                .orElseGet(() -> {
                    String root = canonicalRequested.split("-", 2)[0];
                    return languages.stream()
                            .filter(value -> value.equalsIgnoreCase(root)
                                    || value.toLowerCase(Locale.ROOT).startsWith(root + "-"))
                            .sorted(Comparator
                                    .comparing((String value) -> !value.equalsIgnoreCase(root))
                                    .thenComparingInt(String::length)
                                    .thenComparing(String::compareTo))
                            .findFirst().orElse(null);
                });
    }

    private boolean hasDownloadableTrack(JsonNode formats) {
        if (!formats.isArray()) return false;
        for (JsonNode format : formats) {
            if (format.path("url").isTextual()) return true;
        }
        return false;
    }

    private Path downloadCaption(String canonicalUrl, CaptionSelection selection, Path directory) throws IOException {
        List<String> arguments = new ArrayList<>(List.of(
                "--skip-download", "--no-playlist", "--no-warnings",
                selection.kind() == VideoSourceEntity.CaptionKind.MANUAL ? "--write-subs" : "--write-auto-subs",
                "--sub-langs", selection.language(), "--sub-format", "vtt/best",
                "--convert-subs", "vtt", "--output", "caption.%(ext)s", canonicalUrl
        ));
        YtDlpProcessResultDto result = processRunner.run(
                arguments, directory, configuration.getYoutube().getProcessTimeout(),
                4_096, configuration.getYoutube().getMaxCapturedErrorBytes()
        );
        if (result.exitCode() != 0) throw classifyFailure(result);
        try (var files = Files.list(directory)) {
            List<Path> candidates = files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vtt"))
                    .toList();
            if (candidates.size() != 1) throw permanent("YOUTUBE_CAPTIONS_NOT_FOUND", null);
            return candidates.get(0);
        }
    }

    private YouTubeSubtitleException classifyFailure(YtDlpProcessResultDto result) {
        String safeClassifier = new String(result.stderr(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (safeClassifier.contains("429") || safeClassifier.contains("too many requests")
                || safeClassifier.matches("(?s).*http error 5\\d\\d.*")) {
            return transientFailure("YOUTUBE_PROVIDER_TEMPORARY_FAILURE", null);
        }
        if (safeClassifier.contains("private video") || safeClassifier.contains("video unavailable")
                || safeClassifier.contains("age-restricted")) {
            return permanent("YOUTUBE_VIDEO_UNAVAILABLE", null);
        }
        return transientFailure("YTDLP_EXECUTION_FAILED", null);
    }

    private String canonicalLanguage(String selected, String fallback) {
        try {
            return new LanguageTag(selected).value();
        } catch (IllegalArgumentException exception) {
            return new LanguageTag(fallback).value();
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).isTextual() ? node.path(field).asText().strip() : "";
        return value.isBlank() ? fallback : value.substring(0, Math.min(value.length(), 512));
    }

    private Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("whatsanki-youtube-");
        } catch (IOException exception) {
            throw transientFailure("YOUTUBE_TEMP_DIRECTORY_FAILED", exception);
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            log.warn("Could not fully clean YouTube worker temporary directory");
        }
    }

    private YouTubeSubtitleException permanent(String code, Throwable cause) {
        return new YouTubeSubtitleException(BackgroundJobFailureKind.PERMANENT, code, cause);
    }

    private YouTubeSubtitleException transientFailure(String code, Throwable cause) {
        return new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, code, cause);
    }

    private record CaptionSelection(String language, VideoSourceEntity.CaptionKind kind) { }
}
