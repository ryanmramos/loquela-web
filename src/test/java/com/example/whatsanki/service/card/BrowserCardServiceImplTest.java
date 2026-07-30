package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.CardMutationRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserCardServiceImplTest {

    @Mock private UserIdentityService userIdentityService;
    @Mock private UserLanguageSettingsService languageSettingsService;
    @Mock private CardRepository cardRepository;
    @Mock private DeckRepository deckRepository;
    @InjectMocks private BrowserCardServiceImpl service;

    @Test
    void createsAnOwnedBasicCardUsingCurrentLanguagesAndNormalizedText() {
        UserId userId = UserId.random();
        DeckEntity deck = activeDeck(userId, "Vocabulary");
        CardMutationRequestDto request = new CardMutationRequestDto(
                BrowserReferenceCodec.encode(deck.getId()),
                "  vivir  ",
                "  to live  ",
                "  Ella vive aquí.  ",
                null
        );
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
        when(deckRepository.findByIdAndUserId(deck.getId(), userId.value()))
                .thenReturn(Optional.of(deck));
        when(languageSettingsService.getCurrent(userId))
                .thenReturn(new LanguagePairDto("es", "en"));
        when(cardRepository.save(any(CardEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("subject", request);

        ArgumentCaptor<CardEntity> cardCaptor = ArgumentCaptor.forClass(CardEntity.class);
        verify(cardRepository).save(cardCaptor.capture());
        CardEntity card = cardCaptor.getValue();
        assertThat(card.getUserId()).isEqualTo(userId.value());
        assertThat(card.getDeckId()).isEqualTo(deck.getId());
        assertThat(card.getNoteType()).isEqualTo(CardEntity.NoteType.BASIC);
        assertThat(card.getSourceLanguage()).isEqualTo("es");
        assertThat(card.getDefinitionLanguage()).isEqualTo("en");
        assertThat(card.getTerm()).isEqualTo("vivir");
        assertThat(card.getDefinition()).isEqualTo("to live");
        assertThat(card.getExampleText()).isEqualTo("Ella vive aquí.");
        assertThat(card.getPayload()).containsEntry("schemaVersion", 1);
        assertThat(result.deckName()).isEqualTo("Vocabulary");
    }

    @Test
    void rejectsAStaleEditBeforeChangingOrSavingTheCard() {
        UserId userId = UserId.random();
        DeckEntity deck = activeDeck(userId, "Vocabulary");
        CardEntity card = card(userId, deck);
        card.setVersion(4);
        String cardReference = BrowserReferenceCodec.encode(card.getId());
        CardMutationRequestDto request = new CardMutationRequestDto(
                BrowserReferenceCodec.encode(deck.getId()),
                "vivir",
                "to live",
                null,
                3L
        );
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
        when(cardRepository.findByIdAndUserId(card.getId(), userId.value()))
                .thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.update("subject", cardReference, request))
                .isInstanceOfSatisfying(
                        BrowserSourceConflictException.class,
                        exception -> assertThat(exception.problemCode())
                                .isEqualTo("CARD_VERSION_CONFLICT")
                );

        verify(deckRepository, never()).findByIdAndUserId(any(), any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void permanentlyDeletesOnlyTheOwnedCardResolvedByTheSubject() {
        UserId userId = UserId.random();
        DeckEntity deck = activeDeck(userId, "Vocabulary");
        CardEntity card = card(userId, deck);
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
        when(cardRepository.findByIdAndUserId(card.getId(), userId.value()))
                .thenReturn(Optional.of(card));

        service.delete("subject", BrowserReferenceCodec.encode(card.getId()));

        verify(cardRepository).delete(card);
    }

    private DeckEntity activeDeck(UserId userId, String name) {
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        deck.setName(name);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        return deck;
    }

    private CardEntity card(UserId userId, DeckEntity deck) {
        CardEntity card = new CardEntity();
        card.setId(UUID.randomUUID());
        card.setUserId(userId.value());
        card.setDeckId(deck.getId());
        return card;
    }
}
