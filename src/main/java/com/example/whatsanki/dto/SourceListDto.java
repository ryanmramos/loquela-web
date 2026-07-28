package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

public record SourceListDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<BrowserSourceSummaryDto> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {
    public SourceListDto {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
    }
}
