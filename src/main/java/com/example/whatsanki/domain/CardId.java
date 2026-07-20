package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record CardId(UUID value) {
    public CardId { Objects.requireNonNull(value, "value must not be null"); }
    public static CardId random() { return new CardId(UUID.randomUUID()); }
}
