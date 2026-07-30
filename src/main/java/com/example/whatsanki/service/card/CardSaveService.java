package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.dto.CardSaveResultDto;

import java.time.Instant;

public interface CardSaveService {
    CardSaveResultDto saveLatest(UserId userId, int contextNumber, Instant now);
    CardSaveResultDto saveFromLookup(UserId userId, LookupSessionId lookupSessionId, int contextNumber, DeckId deckId, Instant now);
}
