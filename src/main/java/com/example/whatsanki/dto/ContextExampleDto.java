package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ContextExampleDto(
        UUID passageId,
        SourceId sourceId,
        SourceDocumentEntity.Kind sourceKind,
        String sourceDisplayName,
        LanguageTag sourceLanguage,
        int ordinal,
        String displayText,
        Map<String, Object> location
) {
    public ContextExampleDto {
        Objects.requireNonNull(passageId, "passageId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        Objects.requireNonNull(sourceDisplayName, "sourceDisplayName must not be null");
        Objects.requireNonNull(sourceLanguage, "sourceLanguage must not be null");
        Objects.requireNonNull(displayText, "displayText must not be null");
        location = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(location, "location must not be null")));
    }
}
