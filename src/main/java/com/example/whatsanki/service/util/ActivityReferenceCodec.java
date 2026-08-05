package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.ActivityItemDto;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Keeps resource identifiers and cursor ordering details opaque at the browser boundary. */
public final class ActivityReferenceCodec {
    private static final String SOURCE_PREFIX = "act_src_";
    private static final String LOOKUP_PREFIX = "act_lkp_";
    private static final String EXPORT_PREFIX = "act_exp_";

    private ActivityReferenceCodec() { }

    public static String encode(ActivityItemDto.Category category, UUID id) {
        return prefix(category) + BrowserReferenceCodec.encode(id);
    }

    public static Optional<Reference> decode(String value) {
        if (value == null || !value.equals(value.strip())) {
            return Optional.empty();
        }
        for (ActivityItemDto.Category category : ActivityItemDto.Category.values()) {
            String prefix = prefix(category);
            if (value.startsWith(prefix)) {
                return BrowserReferenceCodec.decode(value.substring(prefix.length()))
                        .map(id -> new Reference(category, id));
            }
        }
        return Optional.empty();
    }

    public static String encodeCursor(Instant updatedAt, ActivityItemDto.Category category, UUID id) {
        String value = updatedAt.getEpochSecond() + "|" + updatedAt.getNano() + "|" + category.name() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    public static Optional<Cursor> decodeCursor(String value) {
        if (value == null || value.isBlank() || value.length() > 160 || !value.equals(value.strip())) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            Instant updatedAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
            ActivityItemDto.Category category = ActivityItemDto.Category.valueOf(parts[2]);
            UUID id = UUID.fromString(parts[3]);
            return Optional.of(new Cursor(updatedAt, category, id));
        } catch (IllegalArgumentException | DateTimeException exception) {
            return Optional.empty();
        }
    }

    private static String prefix(ActivityItemDto.Category category) {
        return switch (category) {
            case SOURCE -> SOURCE_PREFIX;
            case LOOKUP -> LOOKUP_PREFIX;
            case EXPORT -> EXPORT_PREFIX;
        };
    }

    public record Reference(ActivityItemDto.Category category, UUID id) { }
    public record Cursor(Instant updatedAt, ActivityItemDto.Category category, UUID id) { }
}
