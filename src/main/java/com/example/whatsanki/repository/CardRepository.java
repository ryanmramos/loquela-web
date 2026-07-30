package com.example.whatsanki.repository;

import com.example.whatsanki.domain.CardEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
            select card from CardEntity card where card.userId = :userId
            and (:deckId is null or card.deckId = :deckId)
            and (:search = '' or lower(card.term) like lower(concat('%', :search, '%'))
                 or lower(card.definition) like lower(concat('%', :search, '%')))
            """)
    Page<CardEntity> findBrowserPage(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                                     @Param("search") String search, Pageable pageable);
    void delete(CardEntity card);
    long countByUserId(UUID userId);
    long countByUserIdAndDeckId(UUID userId, UUID deckId);
    long countByUserIdAndLastPracticedAtIsNull(UUID userId);
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant createdAt);
}
