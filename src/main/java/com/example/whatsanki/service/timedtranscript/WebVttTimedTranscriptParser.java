package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebVttTimedTranscriptParser extends AbstractTimedTranscriptParser {
    public WebVttTimedTranscriptParser(CaptionTextCleaner cleaner) { super(cleaner); }

    @Override public TimedTranscriptFormat format() { return TimedTranscriptFormat.VTT; }

    @Override
    public TimedTranscriptParseResult parse(String input, int maximumCues,
                                            long maximumCharacters, long maximumTimelineMs) {
        String normalized = normalizeLines(input);
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0 || !(lines[0].equals("WEBVTT") || lines[0].startsWith("WEBVTT "))) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
        List<TimedCaptionCueDto> cues = new ArrayList<>();
        Limits limits = new Limits(maximumCues, maximumCharacters, maximumTimelineMs);
        Counters counters = new Counters();
        int index = 1;
        // WebVTT permits header metadata between the signature and the first
        // blank line. Timing syntax is never valid in this header section.
        while (index < lines.length && !lines[index].isBlank()) {
            if (lines[index].contains("-->")) {
                throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            }
            index++;
        }
        while (index < lines.length) {
            while (index < lines.length && lines[index].isBlank()) index++;
            if (index >= lines.length) break;
            String line = lines[index].strip();
            if (startsBlock(line)) {
                counters.ignoredStructural++;
                index = skipBlock(lines, index + 1);
                continue;
            }
            int timingIndex = line.contains("-->") ? index : index + 1;
            if (timingIndex >= lines.length || !lines[timingIndex].contains("-->")) {
                throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            }
            Timing timing = parseTiming(lines[timingIndex]);
            index = timingIndex + 1;
            // Some YouTube-exported VTT files contain a whitespace-only line
            // between the timing line and the cue payload. It is formatting
            // padding, not the blank line that terminates a cue.
            while (index < lines.length && !lines[index].isEmpty() && lines[index].isBlank()) {
                index++;
            }
            StringBuilder payload = new StringBuilder();
            while (index < lines.length && !lines[index].isBlank()) {
                if (!payload.isEmpty()) payload.append(' ');
                payload.append(lines[index++]);
            }
            addCue(cues, timing.start(), timing.end(), payload.toString(), limits, counters);
        }
        if (cues.isEmpty()) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_EMPTY");
        return new TimedTranscriptParseResult(cues, counters.ignoredStructural,
                counters.strippedMarkup, counters.emptyCue);
    }

    private String normalizeLines(String input) {
        if (input == null) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_EMPTY");
        String value = input.startsWith("\uFEFF") ? input.substring(1) : input;
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private boolean startsBlock(String line) {
        return line.equals("STYLE") || line.equals("REGION") || line.equals("NOTE") || line.startsWith("NOTE ");
    }

    private int skipBlock(String[] lines, int index) {
        while (index < lines.length && !lines[index].isBlank()) index++;
        return index;
    }

    private Timing parseTiming(String line) {
        String[] halves = line.strip().split("\\s+-->\\s+", -1);
        if (halves.length != 2) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        String end = halves[1].strip().split("\\s+", 2)[0];
        return new Timing(timestamp(halves[0].strip()), timestamp(end));
    }
}
