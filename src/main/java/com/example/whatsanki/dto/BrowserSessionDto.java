package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record BrowserSessionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        String displayName,
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String definitionLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LinkedAccountsDto linkedAccounts
) {
    public record LinkedAccountsDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean google,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean whatsapp
    ) {
    }
}
