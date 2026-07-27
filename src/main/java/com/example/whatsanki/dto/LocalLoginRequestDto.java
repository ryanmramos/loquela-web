package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LocalLoginRequestDto(
        @NotBlank
        @Pattern(regexp = "learner|reviewer")
        String accountId
) {
}
