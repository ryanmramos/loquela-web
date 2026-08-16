package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionEntity;
import java.time.Instant;
import java.util.List;

public record BrowserLookupDto(String reference, LookupSessionEntity.Status status, String term,
                               LanguagePairDto languagePair, String errorCode, Instant expiresAt,
                               String lemma, List<SenseDto> senses, List<ContextDto> contexts) {
    public record SenseDto(String partOfSpeech, String definition) { }
    public record ContextDto(int number, String sourceReference, String sourceKind, String sourceDisplayName,
                             int ordinal, String displayText, Integer pageNumber, Long startMs,
                             String youtubeUrl, String originUrl) { }
}
