package com.example.whatsanki.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSourceRequestDto(
        @NotBlank(message = "Enter a source name.")
        @Size(max = 512, message = "The source name must be 512 characters or fewer.")
        String displayName,
        @Min(value = 0, message = "The source version is invalid.")
        long version
) {
}
