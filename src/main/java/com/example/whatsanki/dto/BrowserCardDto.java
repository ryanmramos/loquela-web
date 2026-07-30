package com.example.whatsanki.dto;
import com.example.whatsanki.domain.CardEntity;
import java.time.Instant;
public record BrowserCardDto(String reference, String deckReference, String deckName, CardEntity.NoteType noteType,
                             String term, String definition, String exampleText, long version, Instant updatedAt) { }
