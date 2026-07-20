package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.CardId;
import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.DefaultDeckService;
import com.example.whatsanki.service.util.AnkiCardFieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardSaveServiceImplTest {

    @Mock private LookupSessionRepository lookupSessionRepository;
    @Mock private CardRepository cardRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private DefaultDeckService defaultDeckService;

    private CardSaveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CardSaveServiceImpl(
                lookupSessionRepository,
                cardRepository,
                sourceDocumentRepository,
                defaultDeckService,
                new AnkiCardFieldMapper()
        );
    }

    @Test
    void copiesSelectedContextLanguagesDefinitionAndAttributionIntoCard() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, now);
        DeckId deckId = DeckId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(contextSourceId(session, 2));
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.of(session));
        when(cardRepository.findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
                userId.value(), session.getId(), 2
        )).thenReturn(Optional.empty());
        when(defaultDeckService.resolveOrCreate(userId, now)).thenReturn(deckId);
        when(sourceDocumentRepository.findByIdAndUserId(source.getId(), userId.value()))
                .thenReturn(Optional.of(source));
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardSaveResultDto result = service.saveLatest(userId, 2, now);

        assertThat(result.outcome()).isEqualTo(CardSaveResultDto.Outcome.SAVED);
        ArgumentCaptor<CardEntity> cardCaptor = ArgumentCaptor.forClass(CardEntity.class);
        verify(cardRepository).save(cardCaptor.capture());
        CardEntity card = cardCaptor.getValue();
        assertThat(card.getDeckId()).isEqualTo(deckId.value());
        assertThat(card.getNoteType()).isEqualTo(CardEntity.NoteType.BASIC_CONTEXT);
        assertThat(card.getTerm()).isEqualTo("vivir");
        assertThat(card.getDefinition()).isEqualTo("verb: to live\nnoun: life");
        assertThat(card.getExampleText()).isEqualTo("Ella vive aquí.");
        assertThat(card.getSourceLanguage()).isEqualTo("es");
        assertThat(card.getDefinitionLanguage()).isEqualTo("en");
        assertThat(card.getSourceId()).isEqualTo(source.getId());
        assertThat(card.getSourceLocation()).containsEntry("pageNumber", 8);
        assertThat(card.getAnkiTemplateVersion()).isEqualTo("whatsanki-context-v1");
        assertThat(card.getPayload())
                .containsEntry("schemaVersion", 1)
                .containsEntry("lookupSessionId", session.getId().toString())
                .containsEntry("contextNumber", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> anki = (Map<String, Object>) card.getPayload().get("anki");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) anki.get("fields");
        assertThat(anki).containsEntry("noteTypeName", "WhatsAnki Context v1");
        assertThat(fields).containsEntry("ContextBefore", "Ella ")
                .containsEntry("ContextTarget", "vive")
                .containsEntry("ContextAfter", " aquí.")
                .containsEntry("SourceLocator", "p. 8")
                .containsEntry("TextRecognition", "1")
                .containsEntry("VideoRecognition", "");
    }

    @Test
    void returnsExistingCardWithoutCreatingAnotherDeckOrCard() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, now);
        CardEntity existing = new CardEntity();
        existing.setId(CardId.random().value());
        existing.setDeckId(DeckId.random().value());
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.of(session));
        when(cardRepository.findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
                userId.value(), session.getId(), 1
        )).thenReturn(Optional.of(existing));

        CardSaveResultDto result = service.saveLatest(userId, 1, now);

        assertThat(result.outcome()).isEqualTo(CardSaveResultDto.Outcome.ALREADY_SAVED);
        assertThat(result.cardId().value()).isEqualTo(existing.getId());
        verify(defaultDeckService, never()).resolveOrCreate(any(), any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void preservesCopiedAttributionWhenTheOriginalSourceNoLongerExists() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, now);
        DeckId deckId = DeckId.random();
        UUID sourceId = contextSourceId(session, 1);
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.of(session));
        when(cardRepository.findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
                userId.value(), session.getId(), 1
        )).thenReturn(Optional.empty());
        when(defaultDeckService.resolveOrCreate(userId, now)).thenReturn(deckId);
        when(sourceDocumentRepository.findByIdAndUserId(sourceId, userId.value()))
                .thenReturn(Optional.empty());
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.saveLatest(userId, 1, now).outcome())
                .isEqualTo(CardSaveResultDto.Outcome.SAVED);

        ArgumentCaptor<CardEntity> cardCaptor = ArgumentCaptor.forClass(CardEntity.class);
        verify(cardRepository).save(cardCaptor.capture());
        assertThat(cardCaptor.getValue().getSourceId()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) cardCaptor.getValue().getPayload().get("context");
        assertThat(context)
                .containsEntry("sourceId", sourceId.toString())
                .containsEntry("sourceDisplayName", "Book");
    }

    @Test
    void rejectsNumberOutsideTheImmutableSnapshot() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, now);
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.of(session));

        assertThat(service.saveLatest(userId, 3, now).outcome())
                .isEqualTo(CardSaveResultDto.Outcome.INVALID_SELECTION);
        verify(defaultDeckService, never()).resolveOrCreate(any(), any());
    }

    @Test
    void rejectsAnInvalidContextBeforeCreatingTheDefaultDeck() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, now);
        session.setResultPayload(LookupResultPayloadDto.create(
                LookupResultPayloadDto.fromMap(session.getResultPayload()).dictionaryEntry(),
                List.of(context(SourceId.random(), "Ella estudia aquí.", 7))
        ).toMap());
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.of(session));
        when(cardRepository.findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
                userId.value(), session.getId(), 1
        )).thenReturn(Optional.empty());

        assertThat(service.saveLatest(userId, 1, now).outcome())
                .isEqualTo(CardSaveResultDto.Outcome.INVALID_SNAPSHOT);

        verify(defaultDeckService, never()).resolveOrCreate(any(), any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void distinguishesMissingPendingExpiredAndFailedLookups() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        when(lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        )).thenReturn(Optional.empty());
        when(lookupSessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId.value()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(session(userId, LookupSessionEntity.Status.PENDING, now.plusSeconds(60))))
                .thenReturn(Optional.of(session(userId, LookupSessionEntity.Status.READY, now.minusSeconds(1))))
                .thenReturn(Optional.of(session(userId, LookupSessionEntity.Status.FAILED, now.plusSeconds(60))));

        assertThat(service.saveLatest(userId, 1, now).outcome()).isEqualTo(CardSaveResultDto.Outcome.NO_LOOKUP);
        assertThat(service.saveLatest(userId, 1, now).outcome()).isEqualTo(CardSaveResultDto.Outcome.LOOKUP_PENDING);
        assertThat(service.saveLatest(userId, 1, now).outcome()).isEqualTo(CardSaveResultDto.Outcome.LOOKUP_EXPIRED);
        assertThat(service.saveLatest(userId, 1, now).outcome()).isEqualTo(CardSaveResultDto.Outcome.LOOKUP_FAILED);
    }

    private LookupSessionEntity readySession(UserId userId, Instant now) {
        LookupSessionEntity session = session(userId, LookupSessionEntity.Status.READY, now.plusSeconds(1_800));
        SourceId firstSource = SourceId.random();
        SourceId secondSource = SourceId.random();
        DictionaryEntryDto entry = new DictionaryEntryDto(
                "vivir", "vivir", List.of("vive"),
                List.of(
                        new DictionarySenseDto("verb", "to live", "fake:1"),
                        new DictionarySenseDto("noun", "life", "fake:2")
                ),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto("fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of())
        );
        session.setResultPayload(LookupResultPayloadDto.create(entry, List.of(
                context(firstSource, "Quiero vivir aquí.", 7),
                context(secondSource, "Ella vive aquí.", 8)
        )).toMap());
        return session;
    }

    private LookupSessionEntity session(UserId userId, LookupSessionEntity.Status status, Instant expiresAt) {
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId.value());
        session.setRequestedTerm("vivir");
        session.setNormalizedTerm("vivir");
        session.setSourceLanguage("es");
        session.setDefinitionLanguage("en");
        session.setResultPayload(Map.of("schemaVersion", 1));
        session.setStatus(status);
        session.setErrorCode(status == LookupSessionEntity.Status.FAILED ? "DEFINITION_NOT_FOUND" : null);
        session.setCreatedAt(expiresAt.minusSeconds(60));
        session.setUpdatedAt(expiresAt.minusSeconds(60));
        session.setExpiresAt(expiresAt);
        return session;
    }

    private ContextExampleDto context(SourceId sourceId, String text, int pageNumber) {
        return new ContextExampleDto(
                UUID.randomUUID(), sourceId, SourceDocumentEntity.Kind.PDF, "Book",
                new LanguageTag("es"), 0, text, Map.of("pageNumber", pageNumber)
        );
    }

    private UUID contextSourceId(LookupSessionEntity session, int number) {
        return LookupResultPayloadDto.fromMap(session.getResultPayload()).contexts().get(number - 1).sourceId();
    }
}
