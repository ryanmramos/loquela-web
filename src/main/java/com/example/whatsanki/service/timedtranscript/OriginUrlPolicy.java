package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;

@Component
@RequiredArgsConstructor
public class OriginUrlPolicy {
    private final YouTubeUrlPolicy youTubeUrlPolicy;

    public Origin validate(String raw) {
        if (raw == null || raw.isBlank()) return new Origin(null, null, null);
        String value = raw.strip();
        if (value.length() > 2_048 || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_ORIGIN_INVALID");
        }
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getHost().isBlank()) {
                throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_ORIGIN_INVALID");
            }
            IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES);
            try {
                var youtube = youTubeUrlPolicy.parse(value);
                return new Origin(youtube.canonicalUrl().toString(), "YOUTUBE", youtube.videoId());
            } catch (IllegalArgumentException ignored) {
                String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
                if (host.equals("youtu.be") || host.equals("youtube.com")
                        || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
                    throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_ORIGIN_INVALID");
                }
                // A valid HTTPS URL that is not a supported YouTube form remains
                // provenance only. It is deliberately never resolved or requested.
                return new Origin(uri.toASCIIString(), null, null);
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_ORIGIN_INVALID");
        }
    }

    public record Origin(String canonicalUrl, String provider, String providerId) { }
}
