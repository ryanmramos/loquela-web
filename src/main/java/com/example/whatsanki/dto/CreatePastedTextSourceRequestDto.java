package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePastedTextSourceRequestDto(
        @NotBlank(message = "Paste some text to create a source.")
        @Size(max = 2_000_000, message = "Pasted text is too large.")
        String text
) {
}
