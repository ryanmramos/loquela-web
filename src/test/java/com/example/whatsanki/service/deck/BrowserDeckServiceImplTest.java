package com.example.whatsanki.service.deck;

import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.DeckMutationRequestDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.PracticeSessionRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserDeckException;
import com.example.whatsanki.service.export.DeckExportService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserDeckServiceImplTest {
    @Mock private UserIdentityService userIdentityService;
    @Mock private DeckRepository deckRepository;
    @Mock private CardRepository cardRepository;
    @Mock private ExportRequestRepository exportRequestRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private DeckExportService deckExportService;
    @Mock private ObjectStore objectStore;
    @Mock private TextNormalizer textNormalizer;
    private BrowserDeckServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new BrowserDeckServiceImpl(
                userIdentityService, deckRepository, cardRepository, exportRequestRepository,
                practiceSessionRepository, deckExportService, objectStore, textNormalizer
        );
        userId = UserId.random();
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
    }

    @Test
    void createsAnOwnedDeckWithServerNormalizedUniqueNameAndTheme() {
        when(textNormalizer.normalizeDisplay("  Travel  ")).thenReturn("Travel");
        when(textNormalizer.normalizeForSearch("Travel")).thenReturn("travel");
        when(deckRepository.saveAndFlush(any(DeckEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(
                "subject", new DeckMutationRequestDto("  Travel  ", DeckEntity.ThemeColor.ORANGE)
        );

        assertThat(result.name()).isEqualTo("Travel");
        assertThat(result.themeColor()).isEqualTo(DeckEntity.ThemeColor.ORANGE);
        verify(deckRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(deck ->
                deck.getUserId().equals(userId.value())
                        && deck.getNormalizedName().equals("travel")
                        && deck.getStatus() == DeckEntity.Status.ACTIVE
        ));
    }

    @Test
    void refusesToDeleteADeckThatStillContainsCards() {
        DeckEntity deck = deck("Vocabulary");
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value())).thenReturn(Optional.of(deck));
        when(cardRepository.countByUserIdAndDeckId(userId.value(), deck.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete("subject", BrowserReferenceCodec.encode(deck.getId())))
                .isInstanceOfSatisfying(BrowserDeckException.class,
                        exception -> assertThat(exception.problemCode()).isEqualTo("DECK_NOT_EMPTY"));

        verify(deckRepository, never()).delete(any());
    }

    @Test
    void deletesOnlyAnEmptyDeckWithoutExportReferences() {
        DeckEntity deck = deck("Empty");
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value())).thenReturn(Optional.of(deck));

        service.delete("subject", BrowserReferenceCodec.encode(deck.getId()));

        verify(deckRepository).delete(deck);
    }

    @Test
    void refusesToDeleteAnEmptyDeckReferencedByActivePractice() {
        DeckEntity deck = deck("Active practice");
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value())).thenReturn(Optional.of(deck));
        when(practiceSessionRepository.existsByUserIdAndDeckIdAndStatus(
                userId.value(), deck.getId(),
                com.example.whatsanki.domain.PracticeSessionEntity.Status.ACTIVE
        )).thenReturn(true);

        assertThatThrownBy(() -> service.delete("subject", BrowserReferenceCodec.encode(deck.getId())))
                .isInstanceOfSatisfying(BrowserDeckException.class,
                        exception -> assertThat(exception.problemCode())
                                .isEqualTo("DECK_PRACTICE_ACTIVE"));

        verify(deckRepository, never()).delete(any());
    }

    @Test
    void listsSnapshotNamesAndCardCountsInsteadOfCurrentDeckValues() {
        DeckEntity deck = deck("Renamed");
        ExportRequestEntity export = export(deck, ExportRequestEntity.State.READY);
        export.setSnapshotPayload(Map.of(
                "deck", Map.of("name", "Original name"),
                "cards", List.of(Map.of(), Map.of())
        ));
        export.setExpiresAt(Instant.now().plusSeconds(300));
        export.setArtifactKey("private/key");
        when(exportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()))
                .thenReturn(List.of(export));
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value())).thenReturn(Optional.of(deck));

        var result = service.exports("subject");

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.deckName()).isEqualTo("Original name");
            assertThat(item.cardCount()).isEqualTo(2);
            assertThat(item.downloadAvailable()).isTrue();
        });
    }

    @Test
    void authorizesOnlyReadyUnexpiredOwnedArtifactsWithShortLivedLinks() {
        DeckEntity deck = deck("Vocabulary");
        ExportRequestEntity export = export(deck, ExportRequestEntity.State.READY);
        export.setArtifactKey("users/private/deck.apkg");
        export.setExpiresAt(Instant.now().plusSeconds(600));
        when(exportRequestRepository.findByIdAndUserId(export.getId(), userId.value()))
                .thenReturn(Optional.of(export));
        when(objectStore.createPresignedGetUrl(eq(export.getArtifactKey()), any()))
                .thenReturn(URI.create("https://store.example/signed"));

        URI result = service.download("subject", BrowserReferenceCodec.encode(export.getId()));

        assertThat(result).hasToString("https://store.example/signed");
        verify(objectStore).createPresignedGetUrl(eq(export.getArtifactKey()),
                org.mockito.ArgumentMatchers.argThat(duration -> !duration.isNegative()
                        && duration.toMinutes() <= 5));
    }

    private DeckEntity deck(String name) {
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        deck.setName(name);
        deck.setNormalizedName(name.toLowerCase());
        deck.setThemeColor(DeckEntity.ThemeColor.GREEN);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        deck.setCreatedAt(Instant.now());
        deck.setUpdatedAt(Instant.now());
        return deck;
    }

    private ExportRequestEntity export(DeckEntity deck, ExportRequestEntity.State state) {
        ExportRequestEntity export = new ExportRequestEntity();
        export.setId(UUID.randomUUID());
        export.setUserId(userId.value());
        export.setDeckId(deck.getId());
        export.setState(state);
        export.setSnapshotPayload(Map.of());
        export.setCreatedAt(Instant.now());
        export.setUpdatedAt(Instant.now());
        return export;
    }
}
