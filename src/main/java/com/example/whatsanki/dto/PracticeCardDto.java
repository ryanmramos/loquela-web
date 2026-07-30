package com.example.whatsanki.dto;

import com.example.whatsanki.domain.CardEntity;

/**
 * The answer fields are null until reveal. This contract prevents an
 * unrevealed definition from being serialized into the browser DOM.
 */
public record PracticeCardDto(
        CardEntity.NoteType noteType,
        String term,
        String definition,
        String exampleText,
        String sourceName,
        String sourceLocator,
        String videoId,
        Long videoStartSeconds
) { }
