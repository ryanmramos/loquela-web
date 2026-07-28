package com.example.whatsanki.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateInterfaceLanguageRequestDto(
        @Pattern(regexp = "en|es") String interfaceLanguage
) {
}
