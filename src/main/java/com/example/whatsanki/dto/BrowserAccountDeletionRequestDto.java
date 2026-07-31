package com.example.whatsanki.dto;

import jakarta.validation.constraints.Pattern;

public record BrowserAccountDeletionRequestDto(
        @Pattern(regexp = "DELETE MY ACCOUNT") String confirmationPhrase
) { }
