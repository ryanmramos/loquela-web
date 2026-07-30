package com.example.whatsanki.dto;

import com.example.whatsanki.domain.DeckEntity;

import java.time.Instant;

public record BrowserDeckDto(
        String reference,
        String name,
        DeckEntity.ThemeColor themeColor,
        long cardCount,
        Instant createdAt,
        Instant updatedAt
) { }
