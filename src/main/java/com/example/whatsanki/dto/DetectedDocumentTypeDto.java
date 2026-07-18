package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;

public record DetectedDocumentTypeDto(
        SourceDocumentEntity.Kind sourceKind,
        String mediaType
) {
}
