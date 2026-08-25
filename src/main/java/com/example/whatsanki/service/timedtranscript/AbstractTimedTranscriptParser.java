package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractTimedTranscriptParser implements TimedTranscriptParser {
    private static final Pattern TIMESTAMP = Pattern.compile("(?:(\\d+):)?(\\d{2}):(\\d{2})([.,])(\\d{3})");
    protected final CaptionTextCleaner cleaner;

    AbstractTimedTranscriptParser(CaptionTextCleaner cleaner) { this.cleaner = cleaner; }

    protected long timestamp(String value) {
        Matcher matcher = TIMESTAMP.matcher(value);
        if (!matcher.matches()) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        try {
            long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            long minutes = Long.parseLong(matcher.group(2));
            long seconds = Long.parseLong(matcher.group(3));
            if (minutes > 59 || seconds > 59) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            return Math.addExact(Math.addExact(Math.multiplyExact(hours, 3_600_000L),
                    Math.multiplyExact(minutes, 60_000L)),
                    Math.addExact(Math.multiplyExact(seconds, 1_000L), Long.parseLong(matcher.group(5))));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
    }

    protected void addCue(List<TimedCaptionCueDto> cues, long start, long end, String rawText,
                          Limits limits, Counters counters) {
        // Apply ordering, timeline, text, and cue-count limits before creating the DTO.
        if (start < limits.previousStart || end <= start) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
        limits.previousStart = start;
        if (end > limits.maximumTimelineMs) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_TIMELINE_LIMIT");
        }
        CaptionTextCleaner.Result cleaned = cleaner.clean(rawText);
        if (cleaned.strippedMarkup()) counters.strippedMarkup++;
        if (cleaned.text().isBlank()) {
            counters.emptyCue++;
            return;
        }
        if (cues.size() >= limits.maximumCues) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_CUE_LIMIT");
        }
        limits.characters = Math.addExact(limits.characters, cleaned.text().codePointCount(0, cleaned.text().length()));
        if (limits.characters > limits.maximumCharacters) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_TEXT_LIMIT");
        }
        cues.add(new TimedCaptionCueDto(cues.size(), start, end, cleaned.text()));
    }

    protected record Timing(long start, long end) { }
    protected static final class Counters { int ignoredStructural; int strippedMarkup; int emptyCue; }
    protected static final class Limits {
        final int maximumCues; final long maximumCharacters; final long maximumTimelineMs;
        long previousStart = -1; long characters;
        Limits(int maximumCues, long maximumCharacters, long maximumTimelineMs) {
            this.maximumCues = maximumCues; this.maximumCharacters = maximumCharacters;
            this.maximumTimelineMs = maximumTimelineMs;
        }
    }
}
