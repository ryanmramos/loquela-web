package com.example.whatsanki.service.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Encodes upload intent IDs into opaque values accepted by completion endpoints. */
public final class UploadReferenceCodec {
    private static final String PREFIX = "upl_";
    private static final int ENCODED_LENGTH = 22;

    private UploadReferenceCodec() {
    }

    public static String encode(UUID uploadId) {
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(uploadId.getMostSignificantBits())
                .putLong(uploadId.getLeastSignificantBits())
                .array();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static Optional<UUID> decode(String reference) {
        if (reference == null
                || !reference.startsWith(PREFIX)
                || reference.length() != PREFIX.length() + ENCODED_LENGTH
                || !reference.equals(reference.strip())) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(reference.substring(PREFIX.length()));
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
