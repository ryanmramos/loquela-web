package com.example.whatsanki.repository;

import com.example.whatsanki.domain.ExportRequestEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

public interface ExportRequestRepository extends Repository<ExportRequestEntity, UUID> {
    ExportRequestEntity save(ExportRequestEntity exportRequest);
    Optional<ExportRequestEntity> findByIdAndUserId(UUID id, UUID userId);
    List<ExportRequestEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<ExportRequestEntity> findAllByStateAndExpiresAtLessThanEqualOrderByExpiresAt(
            ExportRequestEntity.State state, Instant expiresAt, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select export from ExportRequestEntity export where export.id = :id and export.userId = :userId")
    Optional<ExportRequestEntity> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
