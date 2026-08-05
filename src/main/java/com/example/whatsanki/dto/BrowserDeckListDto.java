package com.example.whatsanki.dto;

import java.util.List;
import java.util.Objects;

public record BrowserDeckListDto(
        List<BrowserDeckDto> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
    public BrowserDeckListDto {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
    }
}
