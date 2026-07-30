package com.example.whatsanki.service.deck;

import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserDeckDto;
import com.example.whatsanki.dto.BrowserExportDto;
import com.example.whatsanki.dto.DeckMutationRequestDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.PracticeSessionRepository;
import com.example.whatsanki.service.BrowserDeckService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserDeckException;
import com.example.whatsanki.service.exception.DeckExportException;
import com.example.whatsanki.service.export.DeckExportService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrowserDeckServiceImpl implements BrowserDeckService {
    private static final Duration DOWNLOAD_LINK_LIFETIME = Duration.ofMinutes(5);
    private static final int EXPORT_HISTORY_LIMIT = 50;
    private static final List<ExportRequestEntity.State> ACTIVE_EXPORT_STATES =
            List.of(ExportRequestEntity.State.QUEUED, ExportRequestEntity.State.RUNNING);

    private final UserIdentityService userIdentityService;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ExportRequestRepository exportRequestRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final DeckExportService deckExportService;
    private final ObjectStore objectStore;
    private final TextNormalizer textNormalizer;

    @Override
    @Transactional(readOnly = true)
    public List<BrowserDeckDto> list(String subject) {
        UserId userId = user(subject);
        return deckRepository.findAllByUserIdAndStatusOrderByName(userId.value(), DeckEntity.Status.ACTIVE)
                .stream().map(deck -> toDeck(deck, userId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserDeckDto get(String subject, String reference) {
        UserId userId = user(subject);
        return toDeck(activeDeck(userId, reference), userId);
    }

    @Override
    @Transactional
    public BrowserDeckDto create(String subject, DeckMutationRequestDto request) {
        UserId userId = user(subject);
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        apply(deck, request);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        try {
            // Flush inside this boundary so the normalized-name uniqueness error is
            // translated into the stable browser conflict response.
            return toDeck(deckRepository.saveAndFlush(deck), userId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DECK_NAME_CONFLICT");
        }
    }

    @Override
    @Transactional
    public BrowserDeckDto update(String subject, String reference, DeckMutationRequestDto request) {
        UserId userId = user(subject);
        DeckEntity deck = activeDeck(userId, reference);
        apply(deck, request);
        try {
            return toDeck(deckRepository.saveAndFlush(deck), userId);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DECK_NAME_CONFLICT");
        }
    }

    @Override
    @Transactional
    public void delete(String subject, String reference) {
        UserId userId = user(subject);
        DeckEntity deck = activeDeck(userId, reference);
        if (cardRepository.countByUserIdAndDeckId(userId.value(), deck.getId()) > 0) {
            throw conflict("DECK_NOT_EMPTY");
        }
        if (practiceSessionRepository.existsByUserIdAndDeckIdAndStatus(
                userId.value(), deck.getId(),
                com.example.whatsanki.domain.PracticeSessionEntity.Status.ACTIVE
        )) {
            throw conflict("DECK_PRACTICE_ACTIVE");
        }
        if (exportRequestRepository.existsByUserIdAndDeckIdAndStateIn(
                userId.value(), deck.getId(), ACTIVE_EXPORT_STATES
        )) {
            throw conflict("DECK_EXPORT_ACTIVE");
        }
        // Keep completed export history referentially intact. A deck with history can
        // be renamed/recolored, but cannot be physically removed.
        if (exportRequestRepository.countByUserIdAndDeckId(userId.value(), deck.getId()) > 0) {
            throw conflict("DECK_HAS_EXPORT_HISTORY");
        }
        deckRepository.delete(deck);
    }

    @Override
    @Transactional
    public BrowserExportDto export(String subject, String deckReference) {
        UserId userId = user(subject);
        DeckEntity deck = activeDeck(userId, deckReference);
        try {
            var accepted = deckExportService.accept(userId, deck.getName(), deck.getNormalizedName());
            ExportRequestEntity export = exportRequestRepository
                    .findByIdAndUserId(accepted.exportRequestId().value(), userId.value())
                    .orElseThrow(() -> notFound("EXPORT_NOT_FOUND"));
            return toExport(export, deck);
        } catch (DeckExportException exception) {
            throw conflict(exception.errorCode());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrowserExportDto> exports(String subject) {
        UserId userId = user(subject);
        return exportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()).stream()
                .limit(EXPORT_HISTORY_LIMIT)
                .map(export -> toExport(export, ownedDeck(userId, export.getDeckId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserExportDto exportStatus(String subject, String reference) {
        UserId userId = user(subject);
        ExportRequestEntity export = ownedExport(userId, reference);
        return toExport(export, ownedDeck(userId, export.getDeckId()));
    }

    @Override
    @Transactional(readOnly = true)
    public URI download(String subject, String reference) {
        UserId userId = user(subject);
        ExportRequestEntity export = ownedExport(userId, reference);
        Instant now = Instant.now();
        if (export.getState() != ExportRequestEntity.State.READY
                || export.getArtifactKey() == null
                || export.getExpiresAt() == null
                || !export.getExpiresAt().isAfter(now)) {
            throw conflict("EXPORT_DOWNLOAD_UNAVAILABLE");
        }
        Duration remaining = Duration.between(now, export.getExpiresAt());
        return objectStore.createPresignedGetUrl(
                export.getArtifactKey(),
                remaining.compareTo(DOWNLOAD_LINK_LIFETIME) < 0 ? remaining : DOWNLOAD_LINK_LIFETIME
        );
    }

    private void apply(DeckEntity deck, DeckMutationRequestDto request) {
        String displayName = textNormalizer.normalizeDisplay(request.name());
        if (displayName.isBlank()) throw new BrowserDeckException(HttpStatus.BAD_REQUEST, "DECK_NAME_INVALID");
        deck.setName(displayName);
        deck.setNormalizedName(textNormalizer.normalizeForSearch(displayName));
        deck.setThemeColor(request.themeColor());
        deck.setUpdatedAt(Instant.now());
    }

    private BrowserDeckDto toDeck(DeckEntity deck, UserId userId) {
        return new BrowserDeckDto(
                BrowserReferenceCodec.encode(deck.getId()), deck.getName(), deck.getThemeColor(),
                cardRepository.countByUserIdAndDeckId(userId.value(), deck.getId()),
                deck.getCreatedAt(), deck.getUpdatedAt()
        );
    }

    private BrowserExportDto toExport(ExportRequestEntity export, DeckEntity deck) {
        long cardCount = 0;
        Object cards = export.getSnapshotPayload().get("cards");
        if (cards instanceof List<?> snapshots) cardCount = snapshots.size();
        String snapshotName = deck.getName();
        Object deckValue = export.getSnapshotPayload().get("deck");
        if (deckValue instanceof Map<?, ?> snapshotDeck && snapshotDeck.get("name") instanceof String name) {
            snapshotName = name;
        }
        boolean available = export.getState() == ExportRequestEntity.State.READY
                && export.getExpiresAt() != null && export.getExpiresAt().isAfter(Instant.now());
        return new BrowserExportDto(
                BrowserReferenceCodec.encode(export.getId()),
                BrowserReferenceCodec.encode(deck.getId()),
                snapshotName,
                cardCount,
                export.getState(),
                export.getErrorCode(),
                export.getCreatedAt(),
                export.getExpiresAt(),
                available
        );
    }

    private UserId user(String subject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, subject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
    }

    private DeckEntity activeDeck(UserId userId, String reference) {
        DeckEntity deck = ownedDeck(userId, decode(reference, "DECK_NOT_FOUND"));
        if (deck.getStatus() != DeckEntity.Status.ACTIVE) throw notFound("DECK_NOT_FOUND");
        return deck;
    }

    private DeckEntity ownedDeck(UserId userId, UUID deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId.value())
                .orElseThrow(() -> notFound("DECK_NOT_FOUND"));
    }

    private ExportRequestEntity ownedExport(UserId userId, String reference) {
        return exportRequestRepository.findByIdAndUserId(
                decode(reference, "EXPORT_NOT_FOUND"), userId.value()
        ).orElseThrow(() -> notFound("EXPORT_NOT_FOUND"));
    }

    private UUID decode(String reference, String code) {
        return BrowserReferenceCodec.decode(reference).orElseThrow(() -> notFound(code));
    }

    private BrowserDeckException conflict(String code) {
        return new BrowserDeckException(HttpStatus.CONFLICT, code);
    }

    private BrowserDeckException notFound(String code) {
        return new BrowserDeckException(HttpStatus.NOT_FOUND, code);
    }
}
