package com.example.whatsanki.dto;

import com.example.whatsanki.domain.DeckEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeckMutationRequestDto(
        @NotBlank @Size(max = 255) String name,
        @NotNull DeckEntity.ThemeColor themeColor
) { }
