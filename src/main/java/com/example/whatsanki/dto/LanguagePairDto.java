package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;

/**
 * Canonical source/definition language pair used at service boundaries.
 */
public record LanguagePairDto(
        String sourceLanguage,
        String definitionLanguage
) {
    public LanguagePairDto {
        sourceLanguage = new LanguageTag(sourceLanguage).value();
        definitionLanguage = new LanguageTag(definitionLanguage).value();
    }
}
