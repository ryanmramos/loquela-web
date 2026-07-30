package com.example.whatsanki.repository;

import com.example.whatsanki.domain.ExportRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportRequestRepository extends Repository<ExportRequestEntity, UUID> {
    ExportRequestEntity save(ExportRequestEntity exportRequest);
    Optional<ExportRequestEntity> findByIdAndUserId(UUID id, UUID userId);
    List<ExportRequestEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndDeckId(UUID userId, UUID deckId);
    boolean existsByUserIdAndDeckIdAndStateIn(
            UUID userId, UUID deckId, List<ExportRequestEntity.State> states
    );
    List<ExportRequestEntity> findAllByStateAndExpiresAtLessThanEqualOrderByExpiresAt(
            ExportRequestEntity.State state, Instant expiresAt, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select export from ExportRequestEntity export where export.id = :id and export.userId = :userId")
    Optional<ExportRequestEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
