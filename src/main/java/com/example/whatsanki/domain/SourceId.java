package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record SourceId(UUID value) {
    public SourceId { Objects.requireNonNull(value, "value must not be null"); }
    public static SourceId random() { return new SourceId(UUID.randomUUID()); }
}
