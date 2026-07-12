package com.example.whatsanki.domain;

import java.util.IllformedLocaleException;
import java.util.Locale;

public record LanguageTag(String value) {
    public LanguageTag {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Language tag is required");
        try {
            Locale locale = new Locale.Builder().setLanguageTag(value.strip()).build();
            value = locale.toLanguageTag();
            if (value.equals("und")) throw new IllegalArgumentException("Language tag must identify a language");
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag", exception);
        }
    }
}
