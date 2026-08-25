package com.example.whatsanki.service.util;

/** Provides display-preserving and search-oriented text normalization. */
public interface TextNormalizer {
    String normalizeDisplay(String text);

    String normalizeForSearch(String displayText);
}
