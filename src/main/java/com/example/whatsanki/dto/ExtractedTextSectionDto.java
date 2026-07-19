package com.example.whatsanki.dto;

public record ExtractedTextSectionDto(int pageNumber, String text) {
    public ExtractedTextSectionDto {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }
}
