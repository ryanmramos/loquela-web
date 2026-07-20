package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;

import java.util.Objects;

public record LookupExecutionResultDto(
        LookupSessionId lookupSessionId,
        Outcome outcome,
        int contextCount
) {
    public LookupExecutionResultDto {
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (contextCount < 0) {
            throw new IllegalArgumentException("contextCount must not be negative");
        }
    }

    public enum Outcome {
        COMPLETED,
        ALREADY_COMPLETED,
        EXPIRED
    }
}
