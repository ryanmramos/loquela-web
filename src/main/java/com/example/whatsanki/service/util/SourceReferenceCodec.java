package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceId;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Encodes source UUIDs into opaque references used by browser and WhatsApp contracts. */
public final class SourceReferenceCodec {

    private static final int UUID_BYTES = 16;
    private static final int ENCODED_LENGTH = 22;

    private SourceReferenceCodec() {
    }

    public static String encode(SourceId sourceId) {
        UUID value = sourceId.value();
        byte[] bytes = ByteBuffer.allocate(UUID_BYTES)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static Optional<SourceId> decode(String reference) {
        if (reference == null || reference.length() != ENCODED_LENGTH || !reference.equals(reference.strip())) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(reference);
            if (bytes.length != UUID_BYTES) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return Optional.of(new SourceId(new UUID(buffer.getLong(), buffer.getLong())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
