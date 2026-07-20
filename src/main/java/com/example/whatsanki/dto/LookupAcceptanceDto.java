package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;

import java.time.Instant;
import java.util.Objects;

public record LookupAcceptanceDto(
        LookupSessionId lookupSessionId,
        LanguagePairDto languagePair,
        Instant expiresAt
) {
    public LookupAcceptanceDto {
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
