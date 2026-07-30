package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePracticeSessionRequestDto(
        @NotBlank @Size(max = 128) String deckReference
) { }
