package com.example.whatsanki.dto;

import java.util.List;
import java.util.Objects;

public record LanguagePairUpdateResultDto(
        Outcome outcome,
        LanguagePairDto languagePair,
        List<LanguagePairDto> supportedPairs
) {
    public LanguagePairUpdateResultDto {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        supportedPairs = List.copyOf(Objects.requireNonNull(supportedPairs, "supportedPairs must not be null"));
    }

    public enum Outcome {
        UPDATED,
        UNCHANGED,
        UNSUPPORTED
    }
}
