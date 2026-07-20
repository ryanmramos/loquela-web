package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.DeckRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDeckServiceImplTest {

    @Mock private DeckRepository deckRepository;
    @InjectMocks private DefaultDeckServiceImpl service;

    @Test
    void resolvesTheSameDefaultDeckWhetherThisCallCreatedItOrLostAConcurrentInsert() {
        UserId userId = UserId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        deck.setName(DefaultDeckServiceImpl.DEFAULT_DECK_NAME);
        deck.setNormalizedName(DefaultDeckServiceImpl.DEFAULT_DECK_NORMALIZED_NAME);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        when(deckRepository.insertIfAbsent(
                any(), eq(userId.value()), eq("Default"), eq("default"), eq(now)
        )).thenReturn(0);
        when(deckRepository.findByUserIdAndNormalizedName(userId.value(), "default"))
                .thenReturn(Optional.of(deck));

        assertThat(service.resolveOrCreate(userId, now).value()).isEqualTo(deck.getId());
        verify(deckRepository).insertIfAbsent(
                any(), eq(userId.value()), eq("Default"), eq("default"), eq(now)
        );
    }
}
