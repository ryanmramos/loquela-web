package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.util.List;

public record BrowserSessionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        String displayName,
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String definitionLanguage,
        String interfaceLanguage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AccountState accountState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Role> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LinkedAccountsDto linkedAccounts
) {
    public enum AccountState { ACTIVE, DELETING }

    public enum Role { USER, ADMIN }

    public record LinkedAccountsDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean google,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean whatsapp
    ) {
    }
}
