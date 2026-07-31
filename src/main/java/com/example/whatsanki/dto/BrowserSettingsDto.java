package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record BrowserSettingsDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProfileDto profile,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LinkedAccountsDto linkedAccounts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LanguagePairDto languagePair,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<LanguagePairDto> supportedPairs,
        boolean automaticCaptionFallback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String exportDeckPrefix,
        boolean exportIncludeProvenance
) {
    public record ProfileDto(String displayName, String email) { }

    public record LinkedAccountsDto(
            boolean google,
            Instant googleLinkedAt,
            boolean whatsapp
    ) { }
}
