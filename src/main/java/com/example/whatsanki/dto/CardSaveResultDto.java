package com.example.whatsanki.dto;

import com.example.whatsanki.domain.CardId;
import com.example.whatsanki.domain.DeckId;

public record CardSaveResultDto(
        Outcome outcome,
        CardId cardId,
        DeckId deckId,
        int contextNumber
) {
    public enum Outcome {
        SAVED,
        ALREADY_SAVED,
        NO_LOOKUP,
        LOOKUP_PENDING,
        LOOKUP_EXPIRED,
        LOOKUP_FAILED,
        INVALID_SELECTION,
        INVALID_SNAPSHOT
    }

    public static CardSaveResultDto saved(CardId cardId, DeckId deckId, int contextNumber) {
        return new CardSaveResultDto(Outcome.SAVED, cardId, deckId, contextNumber);
    }

    public static CardSaveResultDto alreadySaved(CardId cardId, DeckId deckId, int contextNumber) {
        return new CardSaveResultDto(Outcome.ALREADY_SAVED, cardId, deckId, contextNumber);
    }

    public static CardSaveResultDto unavailable(Outcome outcome, int contextNumber) {
        return new CardSaveResultDto(outcome, null, null, contextNumber);
    }
}
