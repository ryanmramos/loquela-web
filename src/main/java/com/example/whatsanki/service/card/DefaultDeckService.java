package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.UserId;

import java.time.Instant;

public interface DefaultDeckService {
    String DEFAULT_DECK_NORMALIZED_NAME = "default";

    DeckId resolveOrCreate(UserId userId, Instant now);
}
