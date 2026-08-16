package com.example.whatsanki.dto;

public record TimedCaptionCueDto(int ordinal, long startMs, long endMs, String text) {
    public TimedCaptionCueDto {
        if (ordinal < 0 || startMs < 0 || endMs <= startMs || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Caption cue must have increasing time and non-blank text");
        }
        text = text.strip();
    }

    /** Compatibility constructor for legacy YouTube subtitle processing. */
    public TimedCaptionCueDto(long startMs, long endMs, String text) {
        this(0, startMs, endMs, text);
    }
}
