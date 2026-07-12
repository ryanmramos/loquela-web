package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record JobId(UUID value) {
    public JobId { Objects.requireNonNull(value, "value must not be null"); }
    public static JobId random() { return new JobId(UUID.randomUUID()); }
}
