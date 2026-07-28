package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSourceUploadRequestDto(
        @NotBlank(message = "Choose a file.")
        @Size(max = 512, message = "The file name must be 512 characters or fewer.")
        String fileName,
        @NotBlank(message = "The media type is required.")
        @Size(max = 128, message = "The media type is too long.")
        String mediaType,
        @Positive(message = "The file must not be empty.")
        long size,
        @NotBlank(message = "The checksum is required.")
        @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "The checksum must be SHA-256.")
        String sha256
) {
}
