package com.example.whatsanki.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardMutationRequestDto(
        @NotBlank @Size(min = 22, max = 22) String deckReference,
        @NotBlank @Size(max = 256) String term,
        @NotBlank @Size(max = 4000) String definition,
        @Size(max = 4000) String exampleText,
        @Min(0) Long version
) {
}
