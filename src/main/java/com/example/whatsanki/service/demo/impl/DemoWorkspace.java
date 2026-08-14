package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.demo.DemoDtos;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

/** Mutable state guarded exclusively by synchronizing on the workspace instance. */
final class DemoWorkspace {
    final UUID id = UUID.randomUUID();
    final UUID stableDeckId = UUID.randomUUID();
    final Instant createdAt;
    Instant lastAccessedAt;
    String sourceLanguage;
    String targetLanguage;
    long corpusRevision;
    final EnumMap<DemoDtos.SourceType, Source> sources = new EnumMap<>(DemoDtos.SourceType.class);
    final Map<String, Lookup> lookupCache = new LinkedHashMap<>();
    Lookup latestLookup;
    final Set<String> uniqueTerms = new LinkedHashSet<>();
    final ArrayDeque<Instant> lookupBurst = new ArrayDeque<>();
    final LinkedHashMap<String, Card> cards = new LinkedHashMap<>();
    final Map<String, Tombstone> tombstones = new LinkedHashMap<>();
    long deckRevision;
    int exportGenerations;
    CachedExport cachedExport;
    boolean ingestionInFlight;
    final EnumMap<DemoDtos.SourceType, Future<?>> tasks = new EnumMap<>(DemoDtos.SourceType.class);

    DemoWorkspace(Instant now, String sourceLanguage, String targetLanguage) {
        this.createdAt = now;
        this.lastAccessedAt = now;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }

    record Passage(UUID id, int ordinal, String text, Map<String, Object> location) { }
    record Source(String reference, long revision, DemoDtos.SourceType type, String label,
                  DemoDtos.SourceState state, String safeErrorCode, int characterCount,
                  List<Passage> passages, Object retryInput, Instant retryExpiresAt) { }
    record Context(String reference, Passage passage, Source source) { }
    record Lookup(String reference, String cacheKey, String normalizedTerm,
                  DictionaryEntryDto entry, List<Context> contexts, Instant createdAt,
                  boolean cached) { }
    record Card(String reference, DemoDtos.CardKind kind, String originKey, String term,
                String definition, String example, String sourceLabel, String languagePair,
                Instant createdAt, AnkiCardFieldsDto fields) { }
    record Tombstone(Card card, String token, Instant expiresAt, int originalIndex) { }
    record CachedExport(String reference, long deckRevision, Path path, long size) { }
}
