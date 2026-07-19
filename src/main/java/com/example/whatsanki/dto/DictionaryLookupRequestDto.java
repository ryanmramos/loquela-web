package com.example.whatsanki.dto;

import java.util.Objects;

/** Minimal, privacy-preserving request passed to a dictionary provider. */
public record DictionaryLookupRequestDto(
        String requestedTerm,
        LanguagePairDto languagePair
) {
    public DictionaryLookupRequestDto {
        if (requestedTerm == null || requestedTerm.isBlank()) {
            throw new IllegalArgumentException("requestedTerm must not be blank");
        }
        requestedTerm = requestedTerm.strip();
        Objects.requireNonNull(languagePair, "languagePair must not be null");
    }
}
