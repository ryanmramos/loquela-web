package com.example.whatsanki.service;

public interface TextNormalizer {
    String normalizeDisplay(String text);

    String normalizeForSearch(String displayText);
}
