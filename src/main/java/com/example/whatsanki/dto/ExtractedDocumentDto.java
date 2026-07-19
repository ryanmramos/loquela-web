package com.example.whatsanki.dto;

import java.util.List;
import java.util.Objects;

public record ExtractedDocumentDto(List<ExtractedTextSectionDto> sections, long characterCount) {
    public ExtractedDocumentDto {
        Objects.requireNonNull(sections, "sections must not be null");
        if (characterCount < 0) {
            throw new IllegalArgumentException("characterCount must not be negative");
        }
        sections = List.copyOf(sections);
    }
}
