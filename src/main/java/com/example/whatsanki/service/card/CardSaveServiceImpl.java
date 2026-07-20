package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.CardId;
import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.dto.NumberedContextExampleDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.util.AnkiCardFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardSaveServiceImpl implements CardSaveService {

    private static final int CARD_PAYLOAD_SCHEMA_VERSION = 1;

    private final LookupSessionRepository lookupSessionRepository;
    private final CardRepository cardRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final DefaultDeckService defaultDeckService;
    private final AnkiCardFieldMapper ankiCardFieldMapper;

    @Override
    @Transactional
    public CardSaveResultDto saveLatest(UserId userId, int contextNumber, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (contextNumber < 1) {
            return CardSaveResultDto.unavailable(CardSaveResultDto.Outcome.INVALID_SELECTION, contextNumber);
        }

        // Lock the selected lookup row so concurrent SAVE messages for the same
        // candidate serialize before checking the durable card-origin uniqueness key.
        var sessionResult = lookupSessionRepository.findLatestForUpdate(
                userId.value(), LookupSessionEntity.Status.READY, now
        );
        if (sessionResult.isEmpty()) {
            return unavailableResult(userId, contextNumber, now);
        }
        LookupSessionEntity session = sessionResult.orElseThrow();

        LookupResultPayloadDto snapshot;
        try {
            snapshot = LookupResultPayloadDto.fromMap(session.getResultPayload());
        } catch (RuntimeException exception) {
            log.error(
                    "Could not decode lookup snapshot for SAVE userId={} lookupSessionId={}",
                    userId.value(), session.getId(), exception
            );
            return CardSaveResultDto.unavailable(CardSaveResultDto.Outcome.INVALID_SNAPSHOT, contextNumber);
        }

        NumberedContextExampleDto context = snapshot.contexts().stream()
                .filter(candidate -> candidate.number() == contextNumber)
                .findFirst()
                .orElse(null);
        if (context == null) {
            log.info(
                    "Rejected unavailable SAVE selection userId={} lookupSessionId={} contextNumber={} resultCount={}",
                    userId.value(), session.getId(), contextNumber, snapshot.contexts().size()
            );
            return CardSaveResultDto.unavailable(CardSaveResultDto.Outcome.INVALID_SELECTION, contextNumber);
        }

        var existing = cardRepository.findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
                userId.value(), session.getId(), contextNumber
        );
        if (existing.isPresent()) {
            CardEntity card = existing.orElseThrow();
            log.info(
                    "Reused saved lookup card userId={} lookupSessionId={} contextNumber={} cardId={}",
                    userId.value(), session.getId(), contextNumber, card.getId()
            );
            return CardSaveResultDto.alreadySaved(
                    new CardId(card.getId()), new DeckId(card.getDeckId()), contextNumber
            );
        }

        AnkiCardFieldsDto ankiFields;
        try {
            ankiFields = ankiCardFieldMapper.map(snapshot.dictionaryEntry(), context);
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "Could not map saved lookup context to the Anki template userId={} lookupSessionId={} contextNumber={}",
                    userId.value(), session.getId(), contextNumber, exception
            );
            return CardSaveResultDto.unavailable(CardSaveResultDto.Outcome.INVALID_SNAPSHOT, contextNumber);
        }
        DeckId deckId = defaultDeckService.resolveOrCreate(userId, now);
        CardEntity card = new CardEntity();
        card.setId(CardId.random().value());
        card.setUserId(userId.value());
        card.setDeckId(deckId.value());
        card.setNoteType(CardEntity.NoteType.BASIC_CONTEXT);
        card.setSourceLanguage(session.getSourceLanguage());
        card.setDefinitionLanguage(session.getDefinitionLanguage());
        card.setTerm(snapshot.dictionaryEntry().requestedTerm());
        card.setDefinition(formatDefinitions(snapshot.dictionaryEntry().senses()));
        card.setExampleText(context.displayText());
        card.setSavedFromLookupSessionId(session.getId());
        card.setSavedFromContextNumber(contextNumber);
        card.setAnkiTemplateVersion(ankiFields.templateVersion());
        card.setSourceLocation(context.location());
        card.setPayload(cardPayload(session, snapshot, context, ankiFields));
        card.setCreatedAt(now);
        card.setUpdatedAt(now);

        // A source may have been deleted after LOOKUP. The copied attribution in the
        // payload remains authoritative; retain the optional relational link only when
        // the source still belongs to this user.
        sourceDocumentRepository.findByIdAndUserId(context.sourceId(), userId.value())
                .ifPresent(source -> card.setSourceId(source.getId()));

        CardEntity saved = cardRepository.save(card);
        log.info(
                "Saved lookup context as card userId={} lookupSessionId={} contextNumber={} cardId={} deckId={}",
                userId.value(), session.getId(), contextNumber, saved.getId(), deckId.value()
        );
        return CardSaveResultDto.saved(new CardId(saved.getId()), deckId, contextNumber);
    }

    private CardSaveResultDto unavailableResult(UserId userId, int contextNumber, Instant now) {
        var latest = lookupSessionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId.value());
        CardSaveResultDto.Outcome outcome;
        if (latest.isEmpty()) {
            outcome = CardSaveResultDto.Outcome.NO_LOOKUP;
        } else {
            LookupSessionEntity session = latest.orElseThrow();
            outcome = switch (session.getStatus()) {
                case PENDING -> session.getExpiresAt().isAfter(now)
                        ? CardSaveResultDto.Outcome.LOOKUP_PENDING
                        : CardSaveResultDto.Outcome.LOOKUP_EXPIRED;
                case FAILED -> CardSaveResultDto.Outcome.LOOKUP_FAILED;
                case EXPIRED, READY -> CardSaveResultDto.Outcome.LOOKUP_EXPIRED;
            };
        }
        log.info("Could not resolve lookup for SAVE userId={} outcome={}", userId.value(), outcome);
        return CardSaveResultDto.unavailable(outcome, contextNumber);
    }

    private String formatDefinitions(List<DictionarySenseDto> senses) {
        return senses.stream()
                .map(sense -> sense.partOfSpeech() + ": " + sense.definition())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private Map<String, Object> cardPayload(
            LookupSessionEntity session,
            LookupResultPayloadDto snapshot,
            NumberedContextExampleDto context,
            AnkiCardFieldsDto ankiFields
    ) {
        Map<String, Object> lookupPayload = snapshot.toMap();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) lookupPayload.get("contexts");

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", CARD_PAYLOAD_SCHEMA_VERSION);
        payload.put("lookupSessionId", session.getId().toString());
        payload.put("contextNumber", context.number());
        payload.put("dictionary", lookupPayload.get("dictionary"));
        payload.put("context", contexts.get(context.number() - 1));
        payload.put("anki", Map.of(
                "schemaVersion", ankiFields.schemaVersion(),
                "noteTypeName", ankiFields.noteTypeName(),
                "templateVersion", ankiFields.templateVersion(),
                "fields", ankiFields.toFieldMap()
        ));
        return Map.copyOf(payload);
    }
}
