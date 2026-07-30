package com.example.whatsanki.database;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.PracticeResponseEntity;
import com.example.whatsanki.dto.PracticeRatingRequestDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.PracticeResponseRepository;
import com.example.whatsanki.service.BrowserPracticeService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserPracticeException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeSessionIntegrationTest extends PostgreSqlIntegrationTest {
    private final UserIdentityService userIdentityService;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final PracticeResponseRepository responseRepository;
    private final BrowserPracticeService practiceService;

    PracticeSessionIntegrationTest(
            UserIdentityService userIdentityService,
            DeckRepository deckRepository,
            CardRepository cardRepository,
            PracticeResponseRepository responseRepository,
            BrowserPracticeService practiceService
    ) {
        this.userIdentityService = userIdentityService;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.responseRepository = responseRepository;
        this.practiceService = practiceService;
    }

    @Test
    void resumesAnImmutableSnapshotAndAppliesDuplicateRatingOnce() {
        String subject = "practice-" + UUID.randomUUID();
        var userId = userIdentityService.resolveOrCreateGoogle(
                subject, "practice@example.test", "Practice Tester"
        );
        DeckEntity deck = deckRepository.save(deck(userId.value()));
        CardEntity card = cardRepository.save(card(userId.value(), deck.getId()));

        var hidden = practiceService.start(subject, BrowserReferenceCodec.encode(deck.getId()));
        assertThat(hidden.currentCard().definition()).isNull();

        // Edits after session creation are deliberately deferred to the next session.
        card.setDefinition("a changed live-card answer");
        cardRepository.save(card);
        var revealed = practiceService.reveal(subject, hidden.reference());
        assertThat(revealed.currentCard().definition()).isEqualTo("snapshot answer");
        assertThat(revealed.currentCard().videoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(revealed.currentCard().videoStartSeconds()).isEqualTo(522);

        UUID requestId = UUID.randomUUID();
        PracticeRatingRequestDto request = new PracticeRatingRequestDto(
                requestId, PracticeResponseEntity.Rating.GOT_IT
        );
        var completed = practiceService.rate(subject, hidden.reference(), request);
        var duplicate = practiceService.rate(subject, hidden.reference(), request);

        assertThat(completed.status().name()).isEqualTo("COMPLETED");
        assertThat(duplicate.status().name()).isEqualTo("COMPLETED");
        UUID sessionId = BrowserReferenceCodec.decode(hidden.reference()).orElseThrow();
        assertThat(responseRepository.countBySessionId(sessionId)).isEqualTo(1);
        assertThat(practiceService.active(subject)).isEmpty();

        String otherSubject = "practice-other-" + UUID.randomUUID();
        userIdentityService.resolveOrCreateGoogle(
                otherSubject, "other-practice@example.test", "Other Practice Tester"
        );
        assertThatThrownBy(() -> practiceService.get(otherSubject, hidden.reference()))
                .isInstanceOfSatisfying(BrowserPracticeException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                    assertThat(exception.getProblemCode()).isEqualTo("PRACTICE_SESSION_NOT_FOUND");
                });
    }

    private DeckEntity deck(UUID userId) {
        Instant now = Instant.now();
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId);
        deck.setName("Practice deck");
        deck.setNormalizedName("practice deck");
        deck.setThemeColor(DeckEntity.ThemeColor.GREEN);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        deck.setCreatedAt(now);
        deck.setUpdatedAt(now);
        return deck;
    }

    private CardEntity card(UUID userId, UUID deckId) {
        Instant now = Instant.now();
        CardEntity card = new CardEntity();
        card.setId(UUID.randomUUID());
        card.setUserId(userId);
        card.setDeckId(deckId);
        card.setNoteType(CardEntity.NoteType.BASIC_CONTEXT);
        card.setSourceLanguage("es");
        card.setDefinitionLanguage("en");
        card.setTerm("sobremesa");
        card.setDefinition("snapshot answer");
        card.setExampleText("La sobremesa se alargó.");
        card.setPayload(Map.of("schemaVersion", 1));
        card.setSourceLocation(Map.of(
                "videoId", "dQw4w9WgXcQ",
                "playbackStartMs", 522_400
        ));
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        return card;
    }
}
