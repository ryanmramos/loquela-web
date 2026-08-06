package com.example.whatsanki.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminAccessEntryRequestDto(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull AdminAccessRole role,
        @Size(max = 320) String confirmationEmail
) {
}
