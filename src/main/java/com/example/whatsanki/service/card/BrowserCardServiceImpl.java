package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserCardDto;
import com.example.whatsanki.dto.BrowserCardListDto;
import com.example.whatsanki.dto.CardMutationRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.service.BrowserCardService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Card CRUD deliberately owns permanent deletion; undo is a future product-level capability. */
@Service
@RequiredArgsConstructor
public class BrowserCardServiceImpl implements BrowserCardService {
    private static final Map<String, Object> BASIC_PAYLOAD = Map.of("schemaVersion", 1);

    private final UserIdentityService userIdentityService;
    private final UserLanguageSettingsService languageSettingsService;
    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;

    @Override
    @Transactional(readOnly = true)
    public BrowserCardListDto list(String subject, int page, int pageSize, String search, String deckReference) {
        UserId userId = resolveUser(subject);
        UUID deckId = deckReference == null ? null : decodeReference(deckReference);
        var cards = cardRepository.findBrowserPage(
                userId.value(),
                deckId,
                search == null ? "" : search.strip(),
                PageRequest.of(
                        page,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"))
                )
        );
        var items = cards.getContent().stream()
                .map(card -> toDto(card, findDeck(card.getDeckId(), userId)))
                .toList();
        return new BrowserCardListDto(
                items,
                cards.getNumber(),
                cards.getSize(),
                cards.getTotalElements(),
                cards.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserCardDto get(String subject, String reference) {
        UserId userId = resolveUser(subject);
        CardEntity card = findCard(reference, userId);
        return toDto(card, findDeck(card.getDeckId(), userId));
    }

    @Override
    @Transactional
    public BrowserCardDto create(String subject, CardMutationRequestDto request) {
        UserId userId = resolveUser(subject);
        DeckEntity deck = findActiveDeck(userId, request.deckReference());
        LanguagePairDto languagePair = languageSettingsService.getCurrent(userId);

        CardEntity card = new CardEntity();
        card.setId(UUID.randomUUID());
        card.setUserId(userId.value());
        card.setDeckId(deck.getId());
        card.setNoteType(CardEntity.NoteType.BASIC);
        card.setSourceLanguage(languagePair.sourceLanguage());
        card.setDefinitionLanguage(languagePair.definitionLanguage());
        card.setPayload(BASIC_PAYLOAD);
        applyMutation(card, request);

        return toDto(cardRepository.save(card), deck);
    }

    @Override
    @Transactional
    public BrowserCardDto update(String subject, String reference, CardMutationRequestDto request) {
        UserId userId = resolveUser(subject);
        CardEntity card = findCard(reference, userId);
        if (request.version() == null || card.getVersion() != request.version()) {
            throw new BrowserSourceConflictException("CARD_VERSION_CONFLICT");
        }

        DeckEntity deck = findActiveDeck(userId, request.deckReference());
        card.setDeckId(deck.getId());
        applyMutation(card, request);
        return toDto(cardRepository.save(card), deck);
    }

    @Override
    @Transactional
    public void delete(String subject, String reference) {
        UserId userId = resolveUser(subject);
        cardRepository.delete(findCard(reference, userId));
    }

    private void applyMutation(CardEntity card, CardMutationRequestDto request) {
        card.setTerm(request.term().strip());
        card.setDefinition(request.definition().strip());
        card.setExampleText(normalizeOptionalText(request.exampleText()));
        card.setUpdatedAt(Instant.now());
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private CardEntity findCard(String reference, UserId userId) {
        return cardRepository.findByIdAndUserId(decodeReference(reference), userId.value())
                .orElseThrow(BrowserSourceNotFoundException::new);
    }

    private DeckEntity findActiveDeck(UserId userId, String reference) {
        return deckRepository.findByIdAndUserId(decodeReference(reference), userId.value())
                .filter(deck -> deck.getStatus() == DeckEntity.Status.ACTIVE)
                .orElseThrow(() -> new BrowserSourceConflictException("DECK_UNAVAILABLE"));
    }

    private DeckEntity findDeck(UUID deckId, UserId userId) {
        return deckRepository.findByIdAndUserId(deckId, userId.value())
                .orElseThrow(BrowserSourceNotFoundException::new);
    }

    private BrowserCardDto toDto(CardEntity card, DeckEntity deck) {
        return new BrowserCardDto(
                BrowserReferenceCodec.encode(card.getId()),
                BrowserReferenceCodec.encode(deck.getId()),
                deck.getName(),
                card.getNoteType(),
                card.getTerm(),
                card.getDefinition(),
                card.getExampleText(),
                card.getVersion(),
                card.getUpdatedAt()
        );
    }

    private UserId resolveUser(String subject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, subject)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated Google identity is not linked"
                ));
    }

    private UUID decodeReference(String value) {
        return BrowserReferenceCodec.decode(value)
                .orElseThrow(BrowserSourceNotFoundException::new);
    }
}
