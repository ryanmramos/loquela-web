package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.CardId;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CreateBrowserLookupRequestDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupRequestFiltersDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.card.CardSaveService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserLookupServiceImplTest {

    @Mock private UserIdentityService userIdentityService;
    @Mock private UserLanguageSettingsService languageSettingsService;
    @Mock private LookupService lookupService;
    @Mock private LookupSessionRepository lookupSessionRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private DeckRepository deckRepository;
    @Mock private CardSaveService cardSaveService;
    @Mock private TextNormalizer textNormalizer;
    @InjectMocks private BrowserLookupServiceImpl service;

    @Test
    void returnsOnlyTheRequestedPageOfSearchableDeckOptions() {
        UserId userId = UserId.random();
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setName("Travel");
        stubUser(userId);
        when(textNormalizer.normalizeForSearch("Trav")).thenReturn("trav");
        when(deckRepository.findAllByUserIdAndStatusAndNormalizedNameContaining(
                org.mockito.ArgumentMatchers.eq(userId.value()),
                org.mockito.ArgumentMatchers.eq(DeckEntity.Status.ACTIVE),
                org.mockito.ArgumentMatchers.eq("trav"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(deck), PageRequest.of(0, 20), 1_001));

        var result = service.decks("subject", "Trav", 0, 20);

        assertThat(result.items()).singleElement().satisfies(option -> {
            assertThat(option.name()).isEqualTo("Travel");
            assertThat(option.reference()).isEqualTo(BrowserReferenceCodec.encode(deck.getId()));
        });
        assertThat(result.totalItems()).isEqualTo(1_001);
        assertThat(result.totalPages()).isEqualTo(51);
        verify(deckRepository).findAllByUserIdAndStatusAndNormalizedNameContaining(
                org.mockito.ArgumentMatchers.eq(userId.value()),
                org.mockito.ArgumentMatchers.eq(DeckEntity.Status.ACTIVE),
                org.mockito.ArgumentMatchers.eq("trav"),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 20)
        );
    }

    @Test
    void capturesValidatedSourceAndKindFiltersWhenCreatingALookup() {
        UserId userId = UserId.random();
        SourceDocumentEntity source = readySource(userId, "es");
        LookupSessionId lookupSessionId = LookupSessionId.random();
        Instant expiresAt = Instant.parse("2026-07-30T00:00:00Z");
        CreateBrowserLookupRequestDto request = new CreateBrowserLookupRequestDto(
                "vivir",
                List.of(SourceReferenceCodec.encode(new SourceId(source.getId()))),
                Set.of("YOUTUBE_VIDEO")
        );
        stubUser(userId);
        when(languageSettingsService.getCurrent(userId)).thenReturn(new LanguagePairDto("es", "en"));
        when(sourceDocumentRepository.findAllByUserIdAndIdIn(userId.value(), Set.of(source.getId())))
                .thenReturn(List.of(source));
        when(lookupService.acceptBrowser(any(), any(), any()))
                .thenReturn(new LookupAcceptanceDto(
                        lookupSessionId,
                        new LanguagePairDto("es", "en"),
                        expiresAt
                ));

        var result = service.create("subject", request);

        ArgumentCaptor<LookupRequestFiltersDto> filters =
                ArgumentCaptor.forClass(LookupRequestFiltersDto.class);
        verify(lookupService).acceptBrowser(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("vivir"),
                filters.capture()
        );
        assertThat(filters.getValue().sourceIds()).containsExactly(source.getId());
        assertThat(filters.getValue().sourceKinds()).containsExactly("YOUTUBE_VIDEO");
        assertThat(result.reference()).isEqualTo(BrowserReferenceCodec.encode(lookupSessionId.value()));
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void rejectsASelectedSourceThatIsNotReadyForTheCurrentLanguage() {
        UserId userId = UserId.random();
        SourceDocumentEntity source = readySource(userId, "en");
        CreateBrowserLookupRequestDto request = new CreateBrowserLookupRequestDto(
                "vivir",
                List.of(SourceReferenceCodec.encode(new SourceId(source.getId()))),
                Set.of()
        );
        stubUser(userId);
        when(languageSettingsService.getCurrent(userId)).thenReturn(new LanguagePairDto("es", "en"));
        when(sourceDocumentRepository.findAllByUserIdAndIdIn(userId.value(), Set.of(source.getId())))
                .thenReturn(List.of(source));

        assertThatThrownBy(() -> service.create("subject", request))
                .isInstanceOfSatisfying(
                        BrowserSourceConflictException.class,
                        exception -> assertThat(exception.problemCode())
                                .isEqualTo("LOOKUP_SOURCE_UNAVAILABLE")
                );

        verify(lookupService, never()).acceptBrowser(any(), any(), any());
    }

    @Test
    void savesToAnOwnedActiveDeckAndReturnsOpaqueReferences() {
        UserId userId = UserId.random();
        LookupSessionId lookupSessionId = LookupSessionId.random();
        DeckId deckId = DeckId.random();
        CardId cardId = CardId.random();
        DeckEntity deck = new DeckEntity();
        deck.setId(deckId.value());
        deck.setUserId(userId.value());
        deck.setStatus(DeckEntity.Status.ACTIVE);
        stubUser(userId);
        when(deckRepository.findByIdAndUserId(deckId.value(), userId.value()))
                .thenReturn(Optional.of(deck));
        when(cardSaveService.saveFromLookup(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(lookupSessionId),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(deckId),
                any(Instant.class)
        )).thenReturn(CardSaveResultDto.saved(cardId, deckId, 2));

        var result = service.save(
                "subject",
                BrowserReferenceCodec.encode(lookupSessionId.value()),
                2,
                BrowserReferenceCodec.encode(deckId.value())
        );

        assertThat(result.outcome()).isEqualTo("SAVED");
        assertThat(result.cardReference()).isEqualTo(BrowserReferenceCodec.encode(cardId.value()));
        assertThat(result.deckReference()).isEqualTo(BrowserReferenceCodec.encode(deckId.value()));
    }

    @Test
    void mapsCapturedVideoLocationToAValidatedTimestampLink() {
        UserId userId = UserId.random();
        LookupSessionEntity session = readyVideoLookup(userId);
        String reference = BrowserReferenceCodec.encode(session.getId());
        stubUser(userId);
        when(lookupSessionRepository.findByIdAndUserId(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));

        var result = service.get("subject", reference);

        assertThat(result.lemma()).isEqualTo("vivir");
        assertThat(result.senses()).singleElement()
                .satisfies(sense -> assertThat(sense.definition()).isEqualTo("to live"));
        assertThat(result.contexts()).singleElement().satisfies(context -> {
            assertThat(context.startMs()).isEqualTo(90_500L);
            assertThat(context.youtubeUrl())
                    .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s");
            assertThat(context.sourceReference()).hasSize(22);
        });
    }

    @Test
    void returnsContextsWhenTheDictionaryDidNotFindADefinition() {
        UserId userId = UserId.random();
        LookupSessionEntity session = readyVideoLookup(userId);
        session.setErrorCode("DEFINITION_NOT_FOUND");
        ContextExampleDto context = new ContextExampleDto(
                UUID.randomUUID(),
                SourceId.random(),
                SourceDocumentEntity.Kind.PASTED_TEXT,
                "Personal notes",
                new LanguageTag("es"),
                1,
                "Vivir con intención.",
                Map.of()
        );
        session.setResultPayload(LookupResultPayloadDto.create(null, List.of(context)).toMap());
        String reference = BrowserReferenceCodec.encode(session.getId());
        stubUser(userId);
        when(lookupSessionRepository.findByIdAndUserId(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));

        var result = service.get("subject", reference);

        assertThat(result.errorCode()).isEqualTo("DEFINITION_NOT_FOUND");
        assertThat(result.lemma()).isNull();
        assertThat(result.senses()).isEmpty();
        assertThat(result.contexts()).singleElement()
                .satisfies(item -> assertThat(item.displayText()).isEqualTo("Vivir con intención."));
    }

    private void stubUser(UserId userId) {
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
    }

    private SourceDocumentEntity readySource(UserId userId, String language) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(UUID.randomUUID());
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setLanguage(new LanguageTag(language).value());
        return source;
    }

    private LookupSessionEntity readyVideoLookup(UserId userId) {
        LanguagePairDto pair = new LanguagePairDto("es", "en");
        DictionaryEntryDto entry = new DictionaryEntryDto(
                "vivir",
                "vivir",
                List.of("vive"),
                List.of(new DictionarySenseDto("verb", "to live", "sense-1")),
                pair,
                new DictionaryProvenanceDto(
                        "fake",
                        "fixture-v1",
                        "vivir",
                        "",
                        "test",
                        "Test dictionary",
                        Map.of()
                )
        );
        ContextExampleDto context = new ContextExampleDto(
                UUID.randomUUID(),
                SourceId.random(),
                SourceDocumentEntity.Kind.YOUTUBE_VIDEO,
                "Spanish lesson",
                new LanguageTag("es"),
                4,
                "Ella vive aquí.",
                Map.of("videoId", "dQw4w9WgXcQ", "playbackStartMs", 90_500L)
        );
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId.value());
        session.setRequestedTerm("vivir");
        session.setSourceLanguage("es");
        session.setDefinitionLanguage("en");
        session.setStatus(LookupSessionEntity.Status.READY);
        session.setExpiresAt(Instant.parse("2026-07-30T00:00:00Z"));
        session.setResultPayload(LookupResultPayloadDto.create(entry, List.of(context)).toMap());
        return session;
    }
}
