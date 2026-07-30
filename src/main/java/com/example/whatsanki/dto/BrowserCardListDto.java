package com.example.whatsanki.dto;
import java.util.List;
public record BrowserCardListDto(List<BrowserCardDto> items, int page, int pageSize, long totalItems, int totalPages) { }
