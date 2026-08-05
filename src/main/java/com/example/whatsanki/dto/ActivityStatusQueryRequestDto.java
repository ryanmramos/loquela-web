package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ActivityStatusQueryRequestDto(
        @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 64) String> references
) { }
