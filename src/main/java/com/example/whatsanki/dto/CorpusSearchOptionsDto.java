package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CorpusSearchOptionsDto(
        LanguageTag sourceLanguage,
        int limit,
        int maxResultsPerSource,
        Set<UUID> sourceIds,
        Set<String> sourceKinds
) {
    public CorpusSearchOptionsDto {
        Objects.requireNonNull(sourceLanguage, "sourceLanguage must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (maxResultsPerSource < 1) {
            throw new IllegalArgumentException("maxResultsPerSource must be positive");
        }
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        sourceKinds = sourceKinds == null ? Set.of() : Set.copyOf(sourceKinds);
    }

    public CorpusSearchOptionsDto(LanguageTag sourceLanguage, int limit, int maxResultsPerSource) {
        this(sourceLanguage, limit, maxResultsPerSource, Set.of(), Set.of());
    }
}
