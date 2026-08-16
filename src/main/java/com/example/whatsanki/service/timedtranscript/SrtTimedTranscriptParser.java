package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SrtTimedTranscriptParser extends AbstractTimedTranscriptParser {
    public SrtTimedTranscriptParser(CaptionTextCleaner cleaner) { super(cleaner); }

    @Override public TimedTranscriptFormat format() { return TimedTranscriptFormat.SRT; }

    @Override
    public TimedTranscriptParseResult parse(String input, int maximumCues,
                                            long maximumCharacters, long maximumTimelineMs) {
        if (input == null) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_EMPTY");
        String value = input.startsWith("\uFEFF") ? input.substring(1) : input;
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<TimedCaptionCueDto> cues = new ArrayList<>();
        Limits limits = new Limits(maximumCues, maximumCharacters, maximumTimelineMs);
        Counters counters = new Counters();
        int index = 0;
        while (index < lines.length) {
            while (index < lines.length && lines[index].isBlank()) index++;
            if (index >= lines.length) break;
            if (!lines[index].strip().matches("\\d+")) {
                throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            }
            index++;
            if (index >= lines.length || !lines[index].contains("-->")) {
                throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            }
            Timing timing = parseTiming(lines[index++]);
            StringBuilder payload = new StringBuilder();
            while (index < lines.length && !lines[index].isBlank()) {
                if (!payload.isEmpty()) payload.append(' ');
                payload.append(lines[index++]);
            }
            if (payload.isEmpty()) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
            addCue(cues, timing.start(), timing.end(), payload.toString(), limits, counters);
        }
        if (cues.isEmpty()) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_EMPTY");
        return new TimedTranscriptParseResult(cues, 0, counters.strippedMarkup, counters.emptyCue);
    }

    private Timing parseTiming(String line) {
        String[] halves = line.strip().split("\\s+-->\\s+", -1);
        if (halves.length != 2 || halves[1].strip().contains(" ")) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
        return new Timing(timestamp(halves[0].strip()), timestamp(halves[1].strip()));
    }
}
