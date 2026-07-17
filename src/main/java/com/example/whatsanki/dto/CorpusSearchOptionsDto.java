package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;

import java.util.Objects;

public record CorpusSearchOptionsDto(
        LanguageTag sourceLanguage,
        int limit,
        int maxResultsPerSource
) {
    public CorpusSearchOptionsDto {
        Objects.requireNonNull(sourceLanguage, "sourceLanguage must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (maxResultsPerSource < 1) {
            throw new IllegalArgumentException("maxResultsPerSource must be positive");
        }
    }
}
