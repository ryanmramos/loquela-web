package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ActivityStatusQueryResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ActivityItemDto> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeCount
) {
    public ActivityStatusQueryResponseDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
