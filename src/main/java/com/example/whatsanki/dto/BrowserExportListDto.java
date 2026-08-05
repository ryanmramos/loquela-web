package com.example.whatsanki.dto;

import java.util.List;
import java.util.Objects;

public record BrowserExportListDto(
        List<BrowserExportDto> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
    public BrowserExportListDto {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
    }
}
