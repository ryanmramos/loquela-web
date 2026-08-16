package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;

import java.util.List;

public record TimedTranscriptParseResult(
        List<TimedCaptionCueDto> cues,
        int ignoredStructuralBlockCount,
        int strippedMarkupCount,
        int emptyCueCount
) {
    public TimedTranscriptParseResult {
        cues = List.copyOf(cues);
    }
}
