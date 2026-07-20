package com.example.whatsanki.service;

import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.UserId;

import java.time.Instant;

public interface DefaultDeckService {
    DeckId resolveOrCreate(UserId userId, Instant now);
}
