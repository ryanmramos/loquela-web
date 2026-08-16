package com.example.whatsanki.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTimedTranscriptUploadRequestDto(
        @NotBlank @Size(max = 512) String fileName,
        @NotBlank @Size(max = 128) String mediaType,
        @Positive long size,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String sha256,
        @NotBlank @Size(max = 35) String language,
        @Size(max = 2_048) String originUrl,
        @Min(0) long timelineOffsetMs
) { }
