package com.example.whatsanki.dto;

import java.util.UUID;

public record BrowserSessionDto(
        UUID userId,
        String displayName,
        String email,
        String sourceLanguage,
        String definitionLanguage,
        LinkedAccountsDto linkedAccounts
) {
    public record LinkedAccountsDto(
            boolean google,
            boolean whatsapp
    ) {
    }
}
