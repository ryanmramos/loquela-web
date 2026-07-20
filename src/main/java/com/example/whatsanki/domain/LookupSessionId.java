package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record LookupSessionId(UUID value) {
    public LookupSessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LookupSessionId random() {
        return new LookupSessionId(UUID.randomUUID());
    }
}
