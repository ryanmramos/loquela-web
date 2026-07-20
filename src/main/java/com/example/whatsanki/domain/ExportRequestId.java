package com.example.whatsanki.domain;

import java.util.Objects;
import java.util.UUID;

public record ExportRequestId(UUID value) {
    public ExportRequestId { Objects.requireNonNull(value, "value must not be null"); }
    public static ExportRequestId random() { return new ExportRequestId(UUID.randomUUID()); }
}
