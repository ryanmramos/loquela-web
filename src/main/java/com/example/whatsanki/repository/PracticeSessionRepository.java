package com.example.whatsanki.repository;

import com.example.whatsanki.domain.PracticeSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends Repository<PracticeSessionEntity, UUID> {
    PracticeSessionEntity save(PracticeSessionEntity session);
    PracticeSessionEntity saveAndFlush(PracticeSessionEntity session);
    Optional<PracticeSessionEntity> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
            UUID userId, PracticeSessionEntity.Status status
    );
    Optional<PracticeSessionEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndDeckIdAndStatus(
            UUID userId, UUID deckId, PracticeSessionEntity.Status status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from PracticeSessionEntity session
            where session.id = :id and session.userId = :userId
            """)
    Optional<PracticeSessionEntity> findByIdAndUserIdForUpdate(
            @Param("id") UUID id, @Param("userId") UUID userId
    );
}
