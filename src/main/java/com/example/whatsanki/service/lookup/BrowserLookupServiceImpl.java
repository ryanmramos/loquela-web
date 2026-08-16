package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.*;
import com.example.whatsanki.dto.*;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BrowserLookupService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.card.CardSaveService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Website-facing lookup facade: validates browser scope before a durable job is queued. */
@Service
@RequiredArgsConstructor
public class BrowserLookupServiceImpl implements BrowserLookupService {
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{6,64}");
    private final UserIdentityService userIdentityService;
    private final UserLanguageSettingsService languageSettingsService;
    private final LookupService lookupService;
    private final LookupSessionRepository lookupSessionRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final DeckRepository deckRepository;
    private final CardSaveService cardSaveService;
    private final TextNormalizer textNormalizer;

    @Override
    public BrowserLookupAcceptedDto create(String googleSubject, CreateBrowserLookupRequestDto request) {
        UserId user = user(googleSubject);
        Set<UUID> sourceIds = decodeSources(request.sourceReferences());
        Set<String> kinds = normalizeKinds(request.sourceKinds());
        String language = languageSettingsService.getCurrent(user).sourceLanguage();
        if (!sourceIds.isEmpty()) {
            List<SourceDocumentEntity> sources = sourceDocumentRepository.findAllByUserIdAndIdIn(user.value(), sourceIds);
            boolean valid = sources.size() == sourceIds.size() && sources.stream().allMatch(source ->
                    source.getStatus() == SourceDocumentEntity.Status.READY && language.equals(source.getLanguage()));
            if (!valid) throw new BrowserSourceConflictException("LOOKUP_SOURCE_UNAVAILABLE");
        }
        LookupAcceptanceDto accepted = lookupService.acceptBrowser(user, request.term(), new LookupRequestFiltersDto(sourceIds, kinds));
        return new BrowserLookupAcceptedDto(BrowserReferenceCodec.encode(accepted.lookupSessionId().value()), accepted.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserLookupDto get(String googleSubject, String lookupReference) {
        UserId user = user(googleSubject);
        UUID lookupId = decode(lookupReference);
        LookupSessionEntity session = lookupSessionRepository.findByIdAndUserId(
                        lookupId,
                        user.value()
                )
                .orElseThrow(BrowserSourceNotFoundException::new);
        LookupResultPayloadDto snapshot = session.getStatus() == LookupSessionEntity.Status.READY
                ? LookupResultPayloadDto.fromMap(session.getResultPayload())
                : null;
        List<BrowserLookupDto.ContextDto> contexts = snapshot == null
                ? List.of()
                : snapshot.contexts().stream().map(this::context).toList();
        List<BrowserLookupDto.SenseDto> senses = snapshot == null || snapshot.dictionaryEntry() == null
                ? List.of()
                : snapshot.dictionaryEntry().senses().stream()
                .map(sense -> new BrowserLookupDto.SenseDto(
                        sense.partOfSpeech(),
                        sense.definition()
                ))
                .toList();
        return new BrowserLookupDto(
                lookupReference,
                session.getStatus(),
                session.getRequestedTerm(),
                new LanguagePairDto(session.getSourceLanguage(), session.getDefinitionLanguage()),
                session.getErrorCode(),
                session.getExpiresAt(),
                snapshot == null || snapshot.dictionaryEntry() == null ? null : snapshot.dictionaryEntry().lemma(),
                senses,
                contexts
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserDeckOptionListDto decks(String googleSubject, String search, int page, int pageSize) {
        String normalizedSearch = textNormalizer.normalizeForSearch(search == null ? "" : search);
        var result = deckRepository.findAllByUserIdAndStatusAndNormalizedNameContaining(
                user(googleSubject).value(), DeckEntity.Status.ACTIVE, normalizedSearch,
                PageRequest.of(page, pageSize, Sort.by("name").ascending().and(Sort.by("id")))
        );
        return new BrowserDeckOptionListDto(
                result.getContent().stream()
                        .map(deck -> new BrowserDeckOptionDto(
                                BrowserReferenceCodec.encode(deck.getId()), deck.getName()
                        )).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Override
    public BrowserLookupSaveDto save(String googleSubject, String lookupReference, int contextNumber, String deckReference) {
        UserId user = user(googleSubject);
        UUID deckId = decode(deckReference);
        DeckEntity deck = deckRepository.findByIdAndUserId(deckId, user.value())
                .filter(candidate -> candidate.getStatus() == DeckEntity.Status.ACTIVE)
                .orElseThrow(() -> new BrowserSourceConflictException("DECK_UNAVAILABLE"));
        CardSaveResultDto result = cardSaveService.saveFromLookup(
                user,
                new LookupSessionId(decode(lookupReference)),
                contextNumber,
                new DeckId(deck.getId()),
                Instant.now()
        );
        if (result.cardId() == null) {
            throw new BrowserSourceConflictException("LOOKUP_RESULT_UNAVAILABLE");
        }
        return new BrowserLookupSaveDto(
                result.outcome().name(),
                BrowserReferenceCodec.encode(result.cardId().value()),
                BrowserReferenceCodec.encode(result.deckId().value())
        );
    }

    private BrowserLookupDto.ContextDto context(NumberedContextExampleDto value) {
        Map<String, Object> location = value.location();
        Integer page = number(location.get("pageNumber"));
        String videoId = string(location.get("videoId"));
        if (videoId == null) {
            videoId = string(location.get("providerVideoId"));
        }
        Long startMs = videoId == null
                ? longNumber(location.get("originTimestampMs"))
                : longNumber(location.get("playbackStartMs"));
        if (startMs == null) startMs = longNumber(location.get("startMs"));
        String youtube = videoId != null && YOUTUBE_ID.matcher(videoId).matches() && startMs != null && startMs >= 0
                ? "https://www.youtube.com/watch?v=" + videoId + "&t=" + startMs / 1000 + "s"
                : null;
        String genericOrigin = youtube == null ? string(location.get("originUrl")) : null;
        return new BrowserLookupDto.ContextDto(
                value.number(),
                SourceReferenceCodec.encode(new SourceId(value.sourceId())),
                value.sourceKind().name(),
                value.sourceDisplayName(),
                value.ordinal(),
                value.displayText(),
                page,
                startMs,
                youtube,
                genericOrigin
        );
    }

    private Set<UUID> decodeSources(List<String> references) {
        if (references == null) {
            return Set.of();
        }
        Set<UUID> values = new LinkedHashSet<>();
        for (String reference : references) {
            values.add(SourceReferenceCodec.decode(reference)
                    .orElseThrow(BrowserSourceNotFoundException::new)
                    .value());
        }
        return Set.copyOf(values);
    }

    private Set<String> normalizeKinds(Set<String> requested) {
        if (requested == null) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String kind : requested) {
            try {
                values.add(SourceDocumentEntity.Kind.valueOf(kind).name());
            } catch (IllegalArgumentException exception) {
                throw new BrowserSourceConflictException("INVALID_SOURCE_KIND");
            }
        }
        return Set.copyOf(values);
    }

    private UUID decode(String reference) {
        return BrowserReferenceCodec.decode(reference)
                .orElseThrow(BrowserSourceNotFoundException::new);
    }

    private UserId user(String subject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, subject)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated Google identity is not linked"
                ));
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
