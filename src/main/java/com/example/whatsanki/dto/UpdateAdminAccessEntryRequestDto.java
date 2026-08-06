package com.example.whatsanki.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAdminAccessEntryRequestDto(
        @NotNull AdminAccessRole role,
        @NotNull AdminAccessStatus status,
        @Min(0) long version,
        @Size(max = 320) String confirmationEmail
) {
}
