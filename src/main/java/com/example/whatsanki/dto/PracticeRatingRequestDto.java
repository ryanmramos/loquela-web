package com.example.whatsanki.dto;

import com.example.whatsanki.domain.PracticeResponseEntity;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PracticeRatingRequestDto(
        @NotNull UUID requestId,
        @NotNull PracticeResponseEntity.Rating rating
) { }
