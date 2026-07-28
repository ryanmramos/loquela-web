package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateYouTubeSourceRequestDto(
        @NotBlank(message = "Enter a YouTube URL.")
        @Size(max = 2048, message = "The YouTube URL is too long.")
        String url
) {
}
