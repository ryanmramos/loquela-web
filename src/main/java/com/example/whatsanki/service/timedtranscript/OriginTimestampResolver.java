package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OriginTimestampResolver {
    private final WhatsAnkiConfigurationDto configuration;

    public Timestamp resolve(long cueStartMs, long offsetMs) {
        long originTimestamp;
        try {
            originTimestamp = Math.addExact(cueStartMs, offsetMs);
        } catch (ArithmeticException exception) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
        long playback = Math.max(0, cueStartMs - configuration.getTimedTranscript().getPlaybackLead().toMillis());
        try {
            playback = Math.addExact(playback, offsetMs);
        } catch (ArithmeticException exception) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_MALFORMED");
        }
        return new Timestamp(originTimestamp, playback);
    }

    public record Timestamp(long originTimestampMs, long playbackStartMs) { }
}
