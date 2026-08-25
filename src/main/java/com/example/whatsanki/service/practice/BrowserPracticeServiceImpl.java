package com.example.whatsanki.service.practice;

import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.PracticeResponseEntity;
import com.example.whatsanki.domain.PracticeSessionEntity;
import com.example.whatsanki.domain.PracticeSessionItemEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.PracticeCardDto;
import com.example.whatsanki.dto.PracticeRatingRequestDto;
import com.example.whatsanki.dto.PracticeSessionDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.PracticeResponseRepository;
import com.example.whatsanki.repository.PracticeSessionItemRepository;
import com.example.whatsanki.repository.PracticeSessionRepository;
import com.example.whatsanki.service.BrowserPracticeService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserPracticeException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implements non-scheduled practice over an immutable card snapshot. Ratings are
 * retained as session feedback only and never modify Anki or scheduling state.
 */
@Service
@RequiredArgsConstructor
public class BrowserPracticeServiceImpl implements BrowserPracticeService {
    private static final Duration SESSION_LIFETIME = Duration.ofHours(24);
    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final UserIdentityService userIdentityService;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final PracticeResponseRepository responseRepository;

    /** Returns the newest active session, expiring it when its 24-hour window has passed. */
    @Override
    @Transactional
    public Optional<PracticeSessionDto> active(String subject) {
        UserId userId = user(subject);
        Optional<PracticeSessionEntity> active =
                sessionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                        userId.value(), PracticeSessionEntity.Status.ACTIVE
                );
        if (active.isEmpty()) return Optional.empty();
        PracticeSessionEntity session = active.orElseThrow();
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            expire(session);
            return Optional.empty();
        }
        return Optional.of(toDto(session));
    }

    /** Creates one active session and snapshots card content before returning it. */
    @Override
    @Transactional
    public PracticeSessionDto start(String subject, String deckReference) {
        UserId userId = user(subject);
        Optional<PracticeSessionDto> activeSession = active(subject);
        if (activeSession.isPresent()) return activeSession.orElseThrow();

        UUID deckId = decode(deckReference);
        DeckEntity deck = deckRepository.findByIdAndUserId(deckId, userId.value())
                .filter(item -> item.getStatus() == DeckEntity.Status.ACTIVE)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "PRACTICE_DECK_NOT_FOUND"));
        List<CardEntity> cards =
                cardRepository.findAllByUserIdAndDeckIdOrderByCreatedAt(userId.value(), deckId);
        if (cards.isEmpty()) {
            throw problem(HttpStatus.CONFLICT, "PRACTICE_DECK_EMPTY");
        }

        Instant now = Instant.now();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId.value());
        session.setDeckId(deckId);
        session.setDeckName(deck.getName());
        session.setStatus(PracticeSessionEntity.Status.ACTIVE);
        session.setCurrentPosition(0);
        session.setAnswerRevealed(false);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(SESSION_LIFETIME));
        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent start returns the single authoritative active session.
            return sessionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                            userId.value(), PracticeSessionEntity.Status.ACTIVE
                    )
                    .map(this::toDto)
                    .orElseThrow(() -> exception);
        }

        List<PracticeSessionItemEntity> snapshots = new ArrayList<>(cards.size());
        for (int position = 0; position < cards.size(); position++) {
            snapshots.add(snapshot(session, cards.get(position), position));
        }
        itemRepository.saveAll(snapshots);
        return toDto(session);
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeSessionDto get(String subject, String sessionReference) {
        UserId userId = user(subject);
        PracticeSessionEntity session = ownedSession(userId, sessionReference);
        ensureAvailable(session);
        return toDto(session);
    }

    @Override
    @Transactional
    public PracticeSessionDto reveal(String subject, String sessionReference) {
        UserId userId = user(subject);
        PracticeSessionEntity session = lockedSession(userId, sessionReference);
        ensureActive(session);
        if (!session.isAnswerRevealed()) {
            session.setAnswerRevealed(true);
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
        }
        return toDto(session);
    }

    /** Records one rating idempotently and advances the persisted session position. */
    @Override
    @Transactional
    public PracticeSessionDto rate(
            String subject, String sessionReference, PracticeRatingRequestDto request
    ) {
        UserId userId = user(subject);
        PracticeSessionEntity session = lockedSession(userId, sessionReference);
        ensureAvailable(session);
        if (responseRepository.findBySessionIdAndRequestId(session.getId(), request.requestId())
                .isPresent()) {
            return toDto(session);
        }
        ensureActive(session);
        if (!session.isAnswerRevealed()) {
            throw problem(HttpStatus.CONFLICT, "PRACTICE_ANSWER_NOT_REVEALED");
        }

        PracticeSessionItemEntity item = currentItem(session);
        Instant now = Instant.now();
        PracticeResponseEntity response = new PracticeResponseEntity();
        response.setId(UUID.randomUUID());
        response.setSessionId(session.getId());
        response.setUserId(userId.value());
        response.setCardId(item.getCardId());
        response.setPosition(session.getCurrentPosition());
        response.setRequestId(request.requestId());
        response.setRating(request.rating());
        response.setCreatedAt(now);
        responseRepository.save(response);

        // Practice does not schedule the card; this timestamp only powers honest
        // "practiced" product summaries and is intentionally rating-independent.
        cardRepository.findByIdAndUserId(item.getCardId(), userId.value()).ifPresent(card -> {
            card.setLastPracticedAt(now);
            cardRepository.save(card);
        });

        long total = itemRepository.countBySessionIdAndUserId(session.getId(), userId.value());
        int nextPosition = session.getCurrentPosition() + 1;
        session.setCurrentPosition(nextPosition);
        session.setAnswerRevealed(false);
        session.setUpdatedAt(now);
        if (nextPosition >= total) {
            session.setStatus(PracticeSessionEntity.Status.COMPLETED);
            session.setCompletedAt(now);
        }
        sessionRepository.save(session);
        return toDto(session);
    }

    @Override
    @Transactional
    public PracticeSessionDto end(String subject, String sessionReference) {
        UserId userId = user(subject);
        PracticeSessionEntity session = lockedSession(userId, sessionReference);
        if (session.getStatus() == PracticeSessionEntity.Status.ACTIVE) {
            expire(session);
        }
        return toDto(session);
    }

    private PracticeSessionItemEntity snapshot(
            PracticeSessionEntity session, CardEntity card, int position
    ) {
        PracticeSessionItemEntity item = new PracticeSessionItemEntity();
        item.setId(UUID.randomUUID());
        item.setSessionId(session.getId());
        item.setUserId(session.getUserId());
        item.setCardId(card.getId());
        item.setPosition(position);
        item.setNoteType(card.getNoteType());
        item.setTerm(card.getTerm());
        item.setDefinition(card.getDefinition());
        item.setExampleText(card.getExampleText());
        item.setSourceName(ankiField(card.getPayload(), "SourceName"));
        item.setSourceLocator(ankiField(card.getPayload(), "SourceLocator"));
        String videoId = videoId(card.getSourceLocation());
        Long videoStartSeconds = videoStartSeconds(card.getSourceLocation());
        if (videoId != null && videoStartSeconds != null) {
            item.setVideoId(videoId);
            item.setVideoStartSeconds(videoStartSeconds);
        }
        return item;
    }

    private String ankiField(Map<String, Object> payload, String name) {
        if (!(payload.get("anki") instanceof Map<?, ?> anki)
                || !(anki.get("fields") instanceof Map<?, ?> fields)
                || !(fields.get(name) instanceof String value)
                || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private String videoId(Map<String, Object> location) {
        if (location == null || !(location.get("videoId") instanceof String videoId)) {
            return null;
        }
        return YOUTUBE_VIDEO_ID.matcher(videoId).matches() ? videoId : null;
    }

    private Long videoStartSeconds(Map<String, Object> location) {
        if (location == null) return null;
        Object milliseconds = location.containsKey("playbackStartMs")
                ? location.get("playbackStartMs")
                : location.get("startMs");
        return milliseconds instanceof Number number && number.longValue() >= 0
                ? number.longValue() / 1_000
                : null;
    }

    private PracticeSessionDto toDto(PracticeSessionEntity session) {
        long total = itemRepository.countBySessionIdAndUserId(session.getId(), session.getUserId());
        PracticeCardDto card = null;
        if (session.getStatus() == PracticeSessionEntity.Status.ACTIVE
                && session.getCurrentPosition() < total) {
            PracticeSessionItemEntity item = currentItem(session);
            boolean revealed = session.isAnswerRevealed();
            card = new PracticeCardDto(
                    item.getNoteType(),
                    item.getTerm(),
                    revealed ? item.getDefinition() : null,
                    revealed ? item.getExampleText() : null,
                    revealed ? item.getSourceName() : null,
                    revealed ? item.getSourceLocator() : null,
                    revealed ? item.getVideoId() : null,
                    revealed ? item.getVideoStartSeconds() : null
            );
        }
        return new PracticeSessionDto(
                BrowserReferenceCodec.encode(session.getId()),
                BrowserReferenceCodec.encode(session.getDeckId()),
                session.getDeckName(),
                session.getStatus(),
                Math.min(session.getCurrentPosition() + 1, Math.toIntExact(total)),
                total,
                session.isAnswerRevealed(),
                card,
                session.getExpiresAt(),
                session.getCompletedAt()
        );
    }

    private PracticeSessionItemEntity currentItem(PracticeSessionEntity session) {
        return itemRepository.findBySessionIdAndUserIdAndPosition(
                        session.getId(), session.getUserId(), session.getCurrentPosition()
                )
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, "PRACTICE_SESSION_INVALID"));
    }

    private PracticeSessionEntity ownedSession(UserId userId, String reference) {
        return sessionRepository.findByIdAndUserId(decode(reference), userId.value())
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "PRACTICE_SESSION_NOT_FOUND"));
    }

    private PracticeSessionEntity lockedSession(UserId userId, String reference) {
        return sessionRepository.findByIdAndUserIdForUpdate(decode(reference), userId.value())
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "PRACTICE_SESSION_NOT_FOUND"));
    }

    private void ensureAvailable(PracticeSessionEntity session) {
        if (session.getStatus() == PracticeSessionEntity.Status.EXPIRED
                || (session.getStatus() == PracticeSessionEntity.Status.ACTIVE
                && !session.getExpiresAt().isAfter(Instant.now()))) {
            throw problem(HttpStatus.GONE, "PRACTICE_SESSION_EXPIRED");
        }
    }

    private void ensureActive(PracticeSessionEntity session) {
        ensureAvailable(session);
        if (session.getStatus() != PracticeSessionEntity.Status.ACTIVE) {
            throw problem(HttpStatus.CONFLICT, "PRACTICE_SESSION_COMPLETE");
        }
    }

    private void expire(PracticeSessionEntity session) {
        session.setStatus(PracticeSessionEntity.Status.EXPIRED);
        session.setAnswerRevealed(false);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
    }

    private UserId user(String subject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, subject)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated Google identity is not linked"
                ));
    }

    private UUID decode(String reference) {
        return BrowserReferenceCodec.decode(reference)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "PRACTICE_SESSION_NOT_FOUND"));
    }

    private BrowserPracticeException problem(HttpStatus status, String code) {
        return new BrowserPracticeException(status, code);
    }
}
