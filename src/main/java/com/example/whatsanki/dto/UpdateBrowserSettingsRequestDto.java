package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBrowserSettingsRequestDto(
        @NotBlank @Size(max = 35) String sourceLanguage,
        @NotBlank @Size(max = 35) String definitionLanguage,
        boolean automaticCaptionFallback,
        @Size(max = 100) String exportDeckPrefix,
        boolean exportIncludeProvenance
) { }
