package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;

public record DocumentExtractionPreparationDto(
        boolean alreadyReady,
        String objectKey,
        SourceDocumentEntity.Kind kind,
        LanguageTag language
) {
    public static DocumentExtractionPreparationDto ready() {
        return new DocumentExtractionPreparationDto(true, null, null, null);
    }
}
