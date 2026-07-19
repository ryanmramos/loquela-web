package com.example.whatsanki.dto;

import java.util.Locale;

/** One provider-independent definition sense. */
public record DictionarySenseDto(
        String partOfSpeech,
        String definition,
        String providerReference
) {
    public DictionarySenseDto {
        partOfSpeech = required(partOfSpeech, "partOfSpeech").toLowerCase(Locale.ROOT);
        definition = required(definition, "definition");
        providerReference = required(providerReference, "providerReference");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
