package com.example.whatsanki.dto;

import com.example.whatsanki.domain.PracticeSessionEntity;

import java.time.Instant;

public record PracticeSessionDto(
        String reference,
        String deckReference,
        String deckName,
        PracticeSessionEntity.Status status,
        int position,
        long totalCards,
        boolean answerRevealed,
        PracticeCardDto currentCard,
        Instant expiresAt,
        Instant completedAt
) { }
