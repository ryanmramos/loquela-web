package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record DeckId(UUID value) {
    public DeckId { Objects.requireNonNull(value, "value must not be null"); }
    public static DeckId random() { return new DeckId(UUID.randomUUID()); }
}
