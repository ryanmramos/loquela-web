package com.example.whatsanki.service.timedtranscript;

public interface TimedTranscriptParser {
    TimedTranscriptFormat format();
    TimedTranscriptParseResult parse(String input, int maximumCues, long maximumCharacters, long maximumTimelineMs);
}
