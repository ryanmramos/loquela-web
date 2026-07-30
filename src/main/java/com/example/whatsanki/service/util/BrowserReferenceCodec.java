package com.example.whatsanki.service.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Opaque UUID references prevent browser contracts from exposing storage identifiers. */
public final class BrowserReferenceCodec {
    private BrowserReferenceCodec() { }

    public static String encode(UUID value) {
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static Optional<UUID> decode(String value) {
        if (value == null || value.length() != 22 || !value.equals(value.strip())) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length != 16) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
