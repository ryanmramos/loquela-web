package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record InboundMessageId(UUID value) {
    public InboundMessageId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static InboundMessageId random() {
        return new InboundMessageId(UUID.randomUUID());
    }
}
