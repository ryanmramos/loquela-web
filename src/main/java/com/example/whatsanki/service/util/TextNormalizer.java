package com.example.whatsanki.service.util;

public interface TextNormalizer {
    String normalizeDisplay(String text);

    String normalizeForSearch(String displayText);
}
