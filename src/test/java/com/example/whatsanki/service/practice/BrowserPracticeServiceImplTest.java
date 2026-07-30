package com.example.whatsanki.service.practice;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.PracticeResponseEntity;
import com.example.whatsanki.domain.PracticeSessionEntity;
import com.example.whatsanki.domain.PracticeSessionItemEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.PracticeRatingRequestDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.PracticeResponseRepository;
import com.example.whatsanki.repository.PracticeSessionItemRepository;
import com.example.whatsanki.repository.PracticeSessionRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserPracticeException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserPracticeServiceImplTest {
    @Mock private UserIdentityService userIdentityService;
    @Mock private DeckRepository deckRepository;
    @Mock private CardRepository cardRepository;
    @Mock private PracticeSessionRepository sessionRepository;
    @Mock private PracticeSessionItemRepository itemRepository;
    @Mock private PracticeResponseRepository responseRepository;
    private BrowserPracticeServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new BrowserPracticeServiceImpl(
                userIdentityService, deckRepository, cardRepository, sessionRepository,
                itemRepository, responseRepository
        );
        userId = UserId.random();
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
    }

    @Test
    void snapshotsCardsAndDoesNotReturnTheAnswerBeforeReveal() {
        DeckEntity deck = deck();
        CardEntity card = card(deck);
        when(sessionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId.value(), PracticeSessionEntity.Status.ACTIVE
        )).thenReturn(Optional.empty());
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value()))
                .thenReturn(Optional.of(deck));
        when(cardRepository.findAllByUserIdAndDeckIdOrderByCreatedAt(userId.value(), deck.getId()))
                .thenReturn(List.of(card));
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterable<PracticeSessionItemEntity> items = invocation.getArgument(0);
            return (List<PracticeSessionItemEntity>) items;
        });
        when(itemRepository.countBySessionIdAndUserId(any(), any())).thenReturn(1L);
        when(itemRepository.findBySessionIdAndUserIdAndPosition(any(), any(), any(Integer.class)))
                .thenAnswer(invocation -> Optional.of(item(
                        invocation.getArgument(0), card, invocation.getArgument(2)
                )));

        var result = service.start("subject", BrowserReferenceCodec.encode(deck.getId()));

        assertThat(result.currentCard().term()).isEqualTo("sobremesa");
        assertThat(result.currentCard().definition()).isNull();
        assertThat(result.currentCard().exampleText()).isNull();
        assertThat(result.currentCard().videoId()).isNull();
        ArgumentCaptor<Iterable<PracticeSessionItemEntity>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getDefinition()).isEqualTo("time talking after a meal");
            assertThat(snapshot.getSourceName()).isEqualTo("Madrid video");
            assertThat(snapshot.getSourceLocator()).isEqualTo("8:42");
        });
    }

    @Test
    void revealReturnsAnswerFromTheImmutableSnapshot() {
        PracticeSessionEntity session = session(true);
        session.setAnswerRevealed(false);
        CardEntity card = card(deck());
        PracticeSessionItemEntity item = item(session.getId(), card, 0);
        when(sessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));
        when(itemRepository.countBySessionIdAndUserId(session.getId(), userId.value()))
                .thenReturn(1L);
        when(itemRepository.findBySessionIdAndUserIdAndPosition(session.getId(), userId.value(), 0))
                .thenReturn(Optional.of(item));

        var result = service.reveal("subject", BrowserReferenceCodec.encode(session.getId()));

        assertThat(result.answerRevealed()).isTrue();
        assertThat(result.currentCard().definition()).isEqualTo("time talking after a meal");
        assertThat(result.currentCard().videoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(result.currentCard().videoStartSeconds()).isEqualTo(522);
        verify(sessionRepository).save(session);
    }

    @Test
    void rejectsRatingBeforeTheAnswerIsRevealed() {
        PracticeSessionEntity session = session(true);
        when(sessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> service.rate(
                "subject",
                BrowserReferenceCodec.encode(session.getId()),
                new PracticeRatingRequestDto(requestId, PracticeResponseEntity.Rating.GOT_IT)
        )).isInstanceOfSatisfying(
                BrowserPracticeException.class,
                exception -> assertThat(exception.getProblemCode())
                        .isEqualTo("PRACTICE_ANSWER_NOT_REVEALED")
        );

        verify(responseRepository, never()).save(any());
    }

    @Test
    void duplicateRatingRequestDoesNotAdvanceOrWriteTwice() {
        PracticeSessionEntity session = session(true);
        session.setAnswerRevealed(true);
        UUID requestId = UUID.randomUUID();
        when(sessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));
        when(responseRepository.findBySessionIdAndRequestId(session.getId(), requestId))
                .thenReturn(Optional.of(new PracticeResponseEntity()));
        when(itemRepository.countBySessionIdAndUserId(session.getId(), userId.value()))
                .thenReturn(1L);
        when(itemRepository.findBySessionIdAndUserIdAndPosition(session.getId(), userId.value(), 0))
                .thenReturn(Optional.of(item(session.getId(), card(deck()), 0)));

        var result = service.rate(
                "subject",
                BrowserReferenceCodec.encode(session.getId()),
                new PracticeRatingRequestDto(requestId, PracticeResponseEntity.Rating.GOT_IT)
        );

        assertThat(result.position()).isEqualTo(1);
        verify(responseRepository, never()).save(any());
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void completingTheLastCardStoresFeedbackWithoutSchedulingIntervals() {
        PracticeSessionEntity session = session(true);
        session.setAnswerRevealed(true);
        CardEntity card = card(deck());
        when(sessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId.value()))
                .thenReturn(Optional.of(session));
        when(itemRepository.findBySessionIdAndUserIdAndPosition(session.getId(), userId.value(), 0))
                .thenReturn(Optional.of(item(session.getId(), card, 0)));
        when(itemRepository.countBySessionIdAndUserId(session.getId(), userId.value()))
                .thenReturn(1L);
        when(cardRepository.findByIdAndUserId(card.getId(), userId.value()))
                .thenReturn(Optional.of(card));
        when(responseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rate(
                "subject",
                BrowserReferenceCodec.encode(session.getId()),
                new PracticeRatingRequestDto(
                        UUID.randomUUID(), PracticeResponseEntity.Rating.NEEDS_WORK
                )
        );

        assertThat(result.status()).isEqualTo(PracticeSessionEntity.Status.COMPLETED);
        assertThat(result.currentCard()).isNull();
        assertThat(card.getLastPracticedAt()).isNotNull();
        verify(responseRepository).save(any(PracticeResponseEntity.class));
    }

    @Test
    void expiresAnOutdatedActiveSessionInsteadOfResumingIt() {
        PracticeSessionEntity session = session(true);
        session.setExpiresAt(Instant.now().minusSeconds(1));
        when(sessionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId.value(), PracticeSessionEntity.Status.ACTIVE
        )).thenReturn(Optional.of(session));

        assertThat(service.active("subject")).isEmpty();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionEntity.Status.EXPIRED);
        assertThat(session.isAnswerRevealed()).isFalse();
        verify(sessionRepository).save(session);
    }

    private DeckEntity deck() {
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        deck.setName("Spanish Core");
        deck.setNormalizedName("spanish core");
        deck.setThemeColor(DeckEntity.ThemeColor.GREEN);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        return deck;
    }

    private CardEntity card(DeckEntity deck) {
        CardEntity card = new CardEntity();
        card.setId(UUID.randomUUID());
        card.setUserId(userId.value());
        card.setDeckId(deck.getId());
        card.setNoteType(CardEntity.NoteType.BASIC_CONTEXT);
        card.setTerm("sobremesa");
        card.setDefinition("time talking after a meal");
        card.setExampleText("La sobremesa se alargó.");
        card.setPayload(Map.of(
                "anki", Map.of("fields", Map.of(
                        "SourceName", "Madrid video",
                        "SourceLocator", "8:42"
                ))
        ));
        card.setSourceLocation(Map.of(
                "videoId", "dQw4w9WgXcQ",
                "playbackStartMs", 522_400
        ));
        return card;
    }

    private PracticeSessionEntity session(boolean active) {
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId.value());
        session.setDeckId(UUID.randomUUID());
        session.setDeckName("Spanish Core");
        session.setStatus(active
                ? PracticeSessionEntity.Status.ACTIVE
                : PracticeSessionEntity.Status.COMPLETED);
        session.setCurrentPosition(0);
        session.setCreatedAt(Instant.now().minusSeconds(30));
        session.setUpdatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3_600));
        return session;
    }

    private PracticeSessionItemEntity item(UUID sessionId, CardEntity card, int position) {
        PracticeSessionItemEntity item = new PracticeSessionItemEntity();
        item.setId(UUID.randomUUID());
        item.setSessionId(sessionId);
        item.setUserId(userId.value());
        item.setCardId(card.getId());
        item.setPosition(position);
        item.setNoteType(card.getNoteType());
        item.setTerm(card.getTerm());
        item.setDefinition(card.getDefinition());
        item.setExampleText(card.getExampleText());
        item.setSourceName("Madrid video");
        item.setSourceLocator("8:42");
        item.setVideoId("dQw4w9WgXcQ");
        item.setVideoStartSeconds(522L);
        return item;
    }
}
