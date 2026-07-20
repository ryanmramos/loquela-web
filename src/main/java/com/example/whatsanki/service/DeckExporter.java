package com.example.whatsanki.service;

import com.example.whatsanki.dto.ExportedDeckDto;

import java.util.Map;

/** Transport-neutral boundary to the isolated .apkg renderer. */
public interface DeckExporter {
    ExportedDeckDto export(Map<String, Object> snapshot);
}
