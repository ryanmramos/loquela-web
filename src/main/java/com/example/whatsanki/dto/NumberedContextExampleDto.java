package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceDocumentEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable context candidate copied into a lookup session for later SAVE selection. */
public record NumberedContextExampleDto(
        int number,
        UUID passageId,
        UUID sourceId,
        SourceDocumentEntity.Kind sourceKind,
        String sourceDisplayName,
        String sourceLanguage,
        int ordinal,
        String displayText,
        Map<String, Object> location
) {
    public NumberedContextExampleDto {
        if (number < 1) {
            throw new IllegalArgumentException("number must be positive");
        }
        Objects.requireNonNull(passageId, "passageId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        sourceDisplayName = required(sourceDisplayName, "sourceDisplayName");
        sourceLanguage = required(sourceLanguage, "sourceLanguage");
        displayText = required(displayText, "displayText");
        location = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(location, "location must not be null")));
    }

    public static NumberedContextExampleDto from(int number, ContextExampleDto context) {
        Objects.requireNonNull(context, "context must not be null");
        return new NumberedContextExampleDto(
                number,
                context.passageId(),
                context.sourceId().value(),
                context.sourceKind(),
                context.sourceDisplayName(),
                context.sourceLanguage().value(),
                context.ordinal(),
                context.displayText(),
                context.location()
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
