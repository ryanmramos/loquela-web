package com.example.whatsanki.repository;

import com.example.whatsanki.domain.CardEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface CardRepository extends Repository<CardEntity, UUID> {
    CardEntity save(CardEntity card);
    Optional<CardEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<CardEntity> findByUserIdAndSavedFromLookupSessionIdAndSavedFromContextNumber(
            UUID userId,
            UUID lookupSessionId,
            int contextNumber
    );
    List<CardEntity> findAllByUserIdAndDeckIdOrderByCreatedAt(UUID userId, UUID deckId);
    long countByUserId(UUID userId);
    long countByUserIdAndLastPracticedAtIsNull(UUID userId);
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant createdAt);
}
