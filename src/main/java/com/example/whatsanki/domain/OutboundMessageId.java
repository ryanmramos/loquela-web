package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record OutboundMessageId(UUID value) {
    public OutboundMessageId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static OutboundMessageId random() {
        return new OutboundMessageId(UUID.randomUUID());
    }
}
