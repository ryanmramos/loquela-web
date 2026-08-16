package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TimedTranscriptPassageBuilder {
    private static final Pattern TOKEN = Pattern.compile(
            "(?:[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*|_+)"
    );
    private static final int MINIMUM_ROLLING_OVERLAP = 3;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;
    private final OriginTimestampResolver timestampResolver;

    public List<PassageDraftDto> build(List<TimedCaptionCueDto> cues, OriginUrlPolicy.Origin origin,
                                       long timelineOffsetMs) {
        List<PassageDraftDto> passages = new ArrayList<>();
        for (int index = 0; index < cues.size();) {
            int startIndex = index;
            StringBuilder text = new StringBuilder();
            while (index < cues.size()
                    && index - startIndex < configuration.getIngestion().getMaxPassageSentences()) {
                String cue = textNormalizer.normalizeDisplay(cues.get(index).text());
                String candidate = appendWithoutRollingOverlap(text.toString(), cue);
                int candidateTokens = tokenCount(candidate);
                if (!text.isEmpty() && (candidate.length() > configuration.getIngestion().getMaxPassageCharacters()
                        || candidateTokens > configuration.getIngestion().getMaxPassageTokens())) break;
                text.setLength(0);
                text.append(candidate);
                index++;
            }
            TimedCaptionCueDto first = cues.get(startIndex);
            TimedCaptionCueDto last = cues.get(index - 1);
            OriginTimestampResolver.Timestamp resolved = timestampResolver.resolve(first.startMs(), timelineOffsetMs);
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("startMs", first.startMs());
            location.put("endMs", last.endMs());
            location.put("originTimestampMs", resolved.originTimestampMs());
            location.put("playbackStartMs", resolved.playbackStartMs());
            location.put("timelineOffsetMs", timelineOffsetMs);
            if (origin.provider() != null) {
                location.put("provider", origin.provider());
                location.put("videoId", origin.providerId());
            } else if (origin.canonicalUrl() != null) {
                location.put("originUrl", origin.canonicalUrl());
            }
            passages.add(new PassageDraftDto(text.toString(), location, Math.max(tokenCount(text.toString()), 1)));
        }
        return List.copyOf(passages);
    }

    private int tokenCount(String text) {
        String normalized = textNormalizer.normalizeForSearch(text);
        return normalized.isBlank() ? 0 : normalized.split("\\s+").length;
    }

    private String appendWithoutRollingOverlap(String accumulated, String current) {
        if (accumulated.isBlank()) return current;
        Tokens before = tokenize(accumulated);
        Tokens now = tokenize(current);
        if (before.comparison.equals(now.comparison)) return accumulated + " " + current;
        int overlap = largestOverlap(before.comparison, now.comparison);
        if (overlap < MINIMUM_ROLLING_OVERLAP) return accumulated + " " + current;
        String suffix = now.displaySuffix(overlap);
        return suffix.isBlank() ? accumulated : accumulated + " " + suffix;
    }

    private int largestOverlap(List<String> previous, List<String> current) {
        for (int size = Math.min(previous.size(), current.size()); size > 0; size--) {
            if (previous.subList(previous.size() - size, previous.size()).equals(current.subList(0, size))) return size;
        }
        return 0;
    }

    private Tokens tokenize(String text) {
        List<String> comparison = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            comparison.add(Normalizer.normalize(matcher.group(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
            starts.add(matcher.start());
        }
        return new Tokens(text, List.copyOf(comparison), List.copyOf(starts));
    }

    private record Tokens(String display, List<String> comparison, List<Integer> starts) {
        String displaySuffix(int consumedTokens) {
            if (consumedTokens >= starts.size()) return "";
            int start = starts.get(consumedTokens);
            while (start > 0 && Character.isWhitespace(display.charAt(start - 1))) start--;
            while (start > 0 && isOpeningPunctuation(display.charAt(start - 1))) start--;
            return display.substring(start).strip();
        }

        private static boolean isOpeningPunctuation(char value) {
            return "([{¿¡«“‘".indexOf(value) >= 0;
        }
    }
}
