package com.example.whatsanki.dto;

public record TimedCaptionCueDto(long startMs, long endMs, String text) {
    public TimedCaptionCueDto {
        if (startMs < 0 || endMs <= startMs || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Caption cue must have increasing time and non-blank text");
        }
        text = text.strip();
    }
}
