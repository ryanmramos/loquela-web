package com.example.whatsanki.dto;

import java.util.List;
import java.util.Objects;

public record BrowserLanguageSettingsDto(
        LanguagePairDto languagePair,
        List<LanguagePairDto> supportedPairs
) {
    public BrowserLanguageSettingsDto {
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        supportedPairs = List.copyOf(Objects.requireNonNull(supportedPairs, "supportedPairs must not be null"));
    }
}
