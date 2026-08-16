package com.example.whatsanki.dto.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Public DTOs for the anonymous, process-local demo API. */
public final class DemoDtos {
    private DemoDtos() { }

    public enum SourceType { YOUTUBE, TIMED_TRANSCRIPT, PDF, PASTED_TEXT }
    public enum SourceState { QUEUED, PROCESSING, READY, FAILED }
    public enum CardKind { DEFINITION, CONTEXT }

    public record Limits(long pdfBytes, int pdfPages, int pdfCharacters,
                         long timedTranscriptBytes, int timedTranscriptCues, int timedTranscriptCharacters,
                         int pastedTextCharacters, int totalIndexedCharacters,
                         int uniqueLookups, int cards) { }

    public record Capabilities(boolean enabled, boolean ingestionEnabled, boolean timedTranscriptEnabled,
                               List<String> supportedLanguagePairs, Limits limits) { }

    public record LanguagesRequest(@NotBlank @Size(max = 16) String sourceLanguage,
                                   @NotBlank @Size(max = 16) String targetLanguage,
                                   boolean confirmSourceChange) { }

    public record PastedTextRequest(@NotBlank @Size(max = 20_000) String text) { }
    public record LookupRequest(@NotBlank @Size(max = 256) String term) { }
    public record SaveCardRequest(@NotBlank @Size(max = 64) String lookupReference,
                                  @NotNull CardKind kind,
                                  @Size(max = 64) String selectionReference) { }

    public record SourceView(SourceType type, String reference, String label, SourceState state,
                             String safeErrorCode, String youtubeVideoId, int characterCount,
                             int passageCount) { }
    public record DefinitionView(String partOfSpeech, String definition) { }
    public record ContextView(String reference, String text, String sourceLabel,
                              SourceType sourceType, Map<String, Object> location) { }
    public record LookupView(String reference, String term, List<DefinitionView> definitions,
                             List<ContextView> contexts, boolean cached) { }
    public record CardView(String reference, CardKind kind, String term, String definition,
                           String example, String sourceLabel, String languagePair,
                           Instant createdAt) { }
    public record WorkspaceView(Instant createdAt, Instant inactivityExpiresAt,
                                Instant absoluteExpiresAt, String sourceLanguage,
                                String targetLanguage, long corpusRevision,
                                List<SourceView> sources, int indexedCharacters,
                                LookupView latestLookup, List<CardView> cards,
                                long deckRevision, boolean exportReady) { }
    public record CardMutation(CardView card, boolean alreadyAdded) { }
    public record CardRemoval(String undoToken, Instant undoExpiresAt) { }
    public record ExportView(String reference, boolean reused) { }
}
