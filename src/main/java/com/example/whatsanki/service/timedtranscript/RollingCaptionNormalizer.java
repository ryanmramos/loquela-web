package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RollingCaptionNormalizer {
    public static final String VERSION = "1";
    private static final long ADJACENCY_TOLERANCE_MS = 2_000;
    private static final Pattern TOKEN = Pattern.compile(
            "(?:[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*|_+)"
    );

    public Result normalize(List<TimedCaptionCueDto> input) {
        List<TimedCaptionCueDto> output = new ArrayList<>();
        int coalesced = 0;
        for (TimedCaptionCueDto current : input) {
            if (output.isEmpty()) {
                output.add(current);
                continue;
            }
            TimedCaptionCueDto previous = output.get(output.size() - 1);
            TimedCaptionCueDto merged = merge(previous, current);
            if (merged == null) {
                output.add(current);
            } else {
                output.set(output.size() - 1, merged);
                coalesced++;
            }
        }
        return new Result(List.copyOf(output), coalesced);
    }

    private TimedCaptionCueDto merge(TimedCaptionCueDto previous, TimedCaptionCueDto current) {
        if (current.startMs() > previous.endMs() + ADJACENCY_TOLERANCE_MS) return null;
        Tokens before = tokenize(previous.text());
        Tokens now = tokenize(current.text());
        if (before.comparison.equals(now.comparison)) {
            // Very short exact repeats can be genuine speech (for example,
            // consecutive cues that both say "no, no, no"). Require more
            // evidence before treating an exact repeat as rolling captions.
            if (before.comparison.size() <= 3) return null;
            return cue(previous, current, current.text());
        }
        if (startsWith(now.comparison, before.comparison)) {
            return cue(previous, current, current.text());
        }
        int overlap = largestOverlap(before.comparison, now.comparison);
        int shorter = Math.min(before.comparison.size(), now.comparison.size());
        if (overlap < 3 || overlap * 2 < shorter) return null;
        String suffix = now.displaySuffix(overlap);
        // A later rolling-caption frame can be a suffix-only window of the
        // accumulated frame. In that case it adds no new text; replacing the
        // previous value would discard the words that precede the suffix.
        String text = suffix.isBlank() ? previous.text() : previous.text() + " " + suffix;
        return cue(previous, current, text);
    }

    private TimedCaptionCueDto cue(TimedCaptionCueDto previous, TimedCaptionCueDto current, String text) {
        return new TimedCaptionCueDto(previous.ordinal(), previous.startMs(),
                Math.max(previous.endMs(), current.endMs()), text);
    }

    private boolean startsWith(List<String> full, List<String> prefix) {
        return full.size() >= prefix.size() && full.subList(0, prefix.size()).equals(prefix);
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

    public record Result(List<TimedCaptionCueDto> cues, int coalescedCueCount) { }
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
