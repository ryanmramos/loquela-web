package com.example.whatsanki.service.card;

import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.service.card.DefaultDeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDeckServiceImpl implements DefaultDeckService {

    public static final String DEFAULT_DECK_NAME = "Default";
    public static final String DEFAULT_DECK_NORMALIZED_NAME = DefaultDeckService.DEFAULT_DECK_NORMALIZED_NAME;

    private final DeckRepository deckRepository;

    @Override
    @Transactional
    public DeckId resolveOrCreate(UserId userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(now, "now must not be null");

        DeckId candidateId = DeckId.random();
        int inserted = deckRepository.insertIfAbsent(
                candidateId.value(),
                userId.value(),
                DEFAULT_DECK_NAME,
                DEFAULT_DECK_NORMALIZED_NAME,
                now
        );
        var deck = deckRepository.findByUserIdAndNormalizedName(
                userId.value(), DEFAULT_DECK_NORMALIZED_NAME
        ).orElseThrow(() -> new IllegalStateException("Default deck could not be resolved"));

        log.info(
                "Resolved default deck userId={} deckId={} created={}",
                userId.value(), deck.getId(), inserted == 1
        );
        return new DeckId(deck.getId());
    }
}
