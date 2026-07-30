package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;

import java.time.Instant;
import java.util.Objects;

public record LookupExecutionPreparationDto(
        LookupSessionId lookupSessionId,
        String requestedTerm,
        String normalizedTerm,
        LanguagePairDto languagePair,
        LookupRequestFiltersDto filters,
        Instant expiresAt,
        boolean alreadyCompleted,
        boolean expired
) {
    public LookupExecutionPreparationDto(LookupSessionId lookupSessionId, String requestedTerm, String normalizedTerm,
                                        LanguagePairDto languagePair, Instant expiresAt, boolean alreadyCompleted, boolean expired) {
        this(lookupSessionId, requestedTerm, normalizedTerm, languagePair, LookupRequestFiltersDto.none(), expiresAt, alreadyCompleted, expired);
    }
    public LookupExecutionPreparationDto {
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        requestedTerm = required(requestedTerm, "requestedTerm");
        normalizedTerm = required(normalizedTerm, "normalizedTerm");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        filters = filters == null ? LookupRequestFiltersDto.none() : filters;
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (alreadyCompleted && expired) {
            throw new IllegalArgumentException("A completed lookup cannot be prepared as expired");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
