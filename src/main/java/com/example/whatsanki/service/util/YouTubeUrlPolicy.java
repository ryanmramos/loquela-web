package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.YouTubeVideoReferenceDto;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reduces supported user input to one trusted video ID before any job is queued. */
@Component
public class YouTubeUrlPolicy {
    private static final Set<String> WATCH_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> HARMLESS_QUERY_PARAMETERS =
            Set.of("si", "is", "t", "start", "feature", "pp");
    private static final Set<String> WATCH_QUERY_PARAMETERS =
            Set.of("v", "si", "is", "t", "start", "feature", "pp");
    private static final Set<String> PLAYLIST_QUERY_PARAMETERS = Set.of("list", "index");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public YouTubeVideoReferenceDto parse(String rawUrl) {
        try {
            URI uri = new URI(rawUrl == null ? "" : rawUrl.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw invalid();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            Map<String, String> query = queryParameters(uri.getRawQuery());
            String videoId;
            if ("youtu.be".equals(host)) {
                String path = uri.getPath();
                videoId = path != null && path.matches("/[A-Za-z0-9_-]{11}") ? path.substring(1) : null;
                validateQueryParameters(query, HARMLESS_QUERY_PARAMETERS);
            } else if (WATCH_HOSTS.contains(host) && "/watch".equals(uri.getPath())) {
                videoId = query.get("v");
                // Playlist acceptance is intentionally postponed; a video URL carrying
                // list/index parameters can otherwise cause surprising provider behavior.
                validateQueryParameters(query, WATCH_QUERY_PARAMETERS);
            } else {
                throw invalid();
            }
            if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
                throw invalid();
            }
            return new YouTubeVideoReferenceDto(
                    videoId,
                    URI.create("https://www.youtube.com/watch?v=" + videoId)
            );
        } catch (URISyntaxException exception) {
            throw invalid();
        }
    }

    private Map<String, String> queryParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return Map.of();
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String part : rawQuery.split("&", -1)) {
            int split = part.indexOf('=');
            String name = split < 0 ? part : part.substring(0, split);
            String value = split < 0 ? "" : part.substring(split + 1);
            if (name.isBlank() || value.isBlank() || parameters.putIfAbsent(name, value) != null) {
                throw invalid();
            }
        }
        return Map.copyOf(parameters);
    }

    private void validateQueryParameters(Map<String, String> parameters, Set<String> allowed) {
        if (parameters.keySet().stream().anyMatch(PLAYLIST_QUERY_PARAMETERS::contains)
                || !allowed.containsAll(parameters.keySet())) {
            throw invalid();
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("URL must identify one canonical YouTube video");
    }
}
