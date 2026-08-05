package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ActivityPageDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ActivityItemDto> items,
        String nextCursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeCount
) {
    public ActivityPageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
