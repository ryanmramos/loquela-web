package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminAccessEntryListDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminAccessEntryDto> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages
) {
}

